/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2023 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) or the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */
package spoon.support.reflect.reference;

import spoon.SpoonException;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtFormalTypeDeclarer;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.reference.CtActualTypeContainer;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.CtVisitor;
import spoon.support.DerivedProperty;
import spoon.support.UnsettableProperty;

import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CtTypeParameterReferenceImpl extends CtTypeReferenceImpl<Object> implements CtTypeParameterReference {
	private static final long serialVersionUID = 1L;
	// Executable matching erases its parameters and can re-enter declaration lookup through a sibling reference.
	private static final ThreadLocal<Set<CtExecutableReference<?>>> RESOLVING_EXECUTABLE_DECLARATIONS =
			ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));
	private static final String LEXICALLY_BOUND_METADATA = CtTypeParameterReferenceImpl.class.getName() + ".lexicallyBound";


	public CtTypeParameterReferenceImpl() {
	}

	@Override
	public <E extends CtElement> E setParent(CtElement parent) {
		if (parent instanceof CtTypeParameter) {
			putMetadata(LEXICALLY_BOUND_METADATA, true);
		}
		return super.setParent(parent);
	}

	@Override
	public boolean isDefaultBoundingType() {
		return (getBoundingType().equals(getFactory().Type().getDefaultBoundingType()));
	}

	@Override
	public void accept(CtVisitor visitor) {
		visitor.visitCtTypeParameterReference(this);
	}

	@Override
	public boolean isPrimitive() {
		return false;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Class<Object> getActualClass() {
		return (Class<Object>) getBoundingType().getActualClass();
	}

	@Override
	@DerivedProperty
	public List<CtTypeReference<?>> getActualTypeArguments() {
		return emptyList();
	}

	@Override
	@UnsettableProperty
	public <C extends CtActualTypeContainer> C setActualTypeArguments(List<? extends CtTypeReference<?>> actualTypeArguments) {
		return (C) this;
	}

	@Override
	@UnsettableProperty
	public <C extends CtActualTypeContainer> C addActualTypeArgument(CtTypeReference<?> actualTypeArgument) {
		return (C) this;
	}

	@Override
	@UnsettableProperty
	public boolean removeActualTypeArgument(CtTypeReference<?> actualTypeArgument) {
		return false;
	}

	@Override
	@DerivedProperty
	public CtTypeReference<?> getBoundingType() {
		CtTypeParameter typeParam = getDeclaration();
		if (typeParam != null) {
			CtTypeReference<?> typeRef = typeParam.getSuperclass();
			if (typeRef != null) {
				return typeRef;
			}
		}
		return getFactory().Type().getDefaultBoundingType();
	}

	@Override
	protected AnnotatedElement getActualAnnotatedElement() {
		// this is never annotated
		return null;
	}

	@Override
	public CtTypeParameter getDeclaration() {
		if (!isParentInitialized()) {
			return null;
		}

		CtElement typeDeclarer = this;
		CtElement parent = getParent();

		if (parent instanceof CtTypeParameter && Objects.equals(getSimpleName(), ((CtTypeParameter) parent).getSimpleName())) {
			/*
			 * a special case of newly created (unbound) CtTypeParameterReference,
			 * whose CtTypeParameter is linked as parent - to temporary remember CtTypeParameterReference bounds
			 * See ReferenceBuilder#getTypeReference(TypeBinding)
			 */
			return (CtTypeParameter) parent;
		}
		boolean nestedInTypeReference = parent instanceof CtTypeReference;
		while (parent instanceof CtTypeReference) {
			if (!parent.isParentInitialized()) {
				// we might enter in that case because of a call
				// of getSuperInterfaces() for example
				CtTypeReference typeReference = (CtTypeReference) parent;
				typeDeclarer = typeReference.getTypeDeclaration();
				if (typeDeclarer == null) {
					return null;
				}
				break;
			} else {
				parent = parent.getParent();
			}
		}
		CtTypeParameter lexicalDeclaration = nestedInTypeReference
				? findTypeParamDeclarationInParents(this)
				: null;
		if (Boolean.TRUE.equals(getMetadata(LEXICALLY_BOUND_METADATA)) && lexicalDeclaration != null) {
			return lexicalDeclaration;
		}
		if (parent instanceof CtExecutableReference) {
			CtExecutableReference parentExec = (CtExecutableReference) parent;
			if (Objects.nonNull(parentExec.getDeclaringType())
					&& !parentExec.getDeclaringType().equals(typeDeclarer)) {
				CtElement parent2 = getExecutableDeclaration(parentExec);
				if (parent2 instanceof CtExecutable) {
					CtTypeParameter executableDeclaration = findCorrespondingTypeParameter(
							parentExec,
							(CtExecutable<?>) parent2);
					if (executableDeclaration != null) {
						return executableDeclaration;
					}
					typeDeclarer = parent2;
				}
			}
		}
		if (lexicalDeclaration != null) {
			return lexicalDeclaration;
		}

		if (!(typeDeclarer instanceof CtFormalTypeDeclarer)) {
			typeDeclarer = typeDeclarer.getParent(CtFormalTypeDeclarer.class);
		}

		// case #1: we're a type of a method parameter, a local variable, ...
		// the strategy is to look in the parents
		// collecting all formal type declarers of the hierarchy
		while (typeDeclarer != null) {
			CtTypeParameter result = findTypeParamDeclaration((CtFormalTypeDeclarer) typeDeclarer, this.getSimpleName());
			if (result != null) {
				return result;
			}
			typeDeclarer = typeDeclarer.getParent(CtFormalTypeDeclarer.class);
		}
		return null;
	}

	private CtElement getExecutableDeclaration(CtExecutableReference<?> executableReference) {
		Set<CtExecutableReference<?>> resolving = RESOLVING_EXECUTABLE_DECLARATIONS.get();
		if (!resolving.add(executableReference)) {
			return null;
		}
		try {
			return executableReference.getExecutableDeclaration();
		} finally {
			resolving.remove(executableReference);
			if (resolving.isEmpty()) {
				RESOLVING_EXECUTABLE_DECLARATIONS.remove();
			}
		}
	}

	private CtTypeParameter findCorrespondingTypeParameter(
			CtExecutableReference<?> executableReference,
			CtExecutable<?> executableDeclaration) {
		List<CtTypeReference<?>> referenceParameters = executableReference.getParameters();
		for (int index = 0; index < referenceParameters.size() && index < executableDeclaration.getParameters().size(); index++) {
			CtTypeParameter result = findCorrespondingTypeParameter(
					referenceParameters.get(index),
					executableDeclaration.getParameters().get(index).getType());
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	private CtTypeParameter findCorrespondingTypeParameter(
			CtTypeReference<?> referenceType,
			CtTypeReference<?> declarationType) {
		if (referenceType == null || declarationType == null) {
			return null;
		}
		if (referenceType == this) {
			if (declarationType instanceof CtTypeParameterReference
					&& declarationType.getSimpleName().equals(getSimpleName())) {
				return ((CtTypeParameterReference) declarationType).getDeclaration();
			}
			return null;
		}
		if (referenceType instanceof CtWildcardReference && declarationType instanceof CtWildcardReference) {
			if (((CtWildcardReference) referenceType).isUpper()
					!= ((CtWildcardReference) declarationType).isUpper()) {
				return null;
			}
			return findCorrespondingTypeParameter(
					((CtWildcardReference) referenceType).getBoundingType(),
					((CtWildcardReference) declarationType).getBoundingType());
		}
		if (referenceType instanceof CtArrayTypeReference && declarationType instanceof CtArrayTypeReference) {
			return findCorrespondingTypeParameter(
					((CtArrayTypeReference<?>) referenceType).getComponentType(),
					((CtArrayTypeReference<?>) declarationType).getComponentType());
		}
		if (referenceType instanceof CtWildcardReference
				|| declarationType instanceof CtWildcardReference
				|| referenceType instanceof CtArrayTypeReference
				|| declarationType instanceof CtArrayTypeReference
				|| !Objects.equals(referenceType.getQualifiedName(), declarationType.getQualifiedName())) {
			return null;
		}
		CtTypeParameter result = findCorrespondingTypeParameter(
				referenceType.getDeclaringType(), declarationType.getDeclaringType());
		if (result != null) {
			return result;
		}
		List<CtTypeReference<?>> referenceArguments = referenceType.getActualTypeArguments();
		List<CtTypeReference<?>> declarationArguments = declarationType.getActualTypeArguments();
		for (int index = 0; index < referenceArguments.size() && index < declarationArguments.size(); index++) {
			result = findCorrespondingTypeParameter(
					referenceArguments.get(index),
					declarationArguments.get(index));
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	private CtTypeParameter findTypeParamDeclarationInParents(CtElement element) {
		CtFormalTypeDeclarer typeDeclarer = element.getParent(CtFormalTypeDeclarer.class);
		return findTypeParamDeclarationInHierarchy(typeDeclarer);
	}

	private CtTypeParameter findTypeParamDeclarationInHierarchy(CtFormalTypeDeclarer typeDeclarer) {
		while (typeDeclarer != null) {
			CtTypeParameter result = findTypeParamDeclaration(typeDeclarer, getSimpleName());
			if (result != null) {
				return result;
			}
			typeDeclarer = ((CtElement) typeDeclarer).getParent(CtFormalTypeDeclarer.class);
		}
		return null;
	}

	private CtTypeParameter findTypeParamDeclaration(CtFormalTypeDeclarer type, String refName) {
		for (CtTypeParameter typeParam : type.getFormalCtTypeParameters()) {
			if (typeParam.getSimpleName().equals(refName)) {
				return typeParam;
			}
		}
		return null;
	}

	@Override
	public CtType<Object> getTypeDeclaration() {
		return getDeclaration();
	}

	@Override
	public CtTypeReference<?> getTypeErasure() {
		CtTypeParameter typeParam = getDeclaration();
		if (typeParam == null) {
			throw new SpoonException("Cannot resolve type erasure of the type parameter reference, which is not able to found it's declaration.");
		}
		return typeParam.getTypeErasure();
	}

	@Override
	public boolean isSubtypeOf(CtTypeReference<?> type) {
		return getTypeDeclaration().isSubtypeOf(type);
	}

	@Override
	public CtTypeParameterReference clone() {
		return (CtTypeParameterReference) super.clone();
	}

	@Override
	public boolean isGenerics() {
		if (getDeclaration() instanceof CtTypeParameter) {
			return true;
		}
		return getBoundingType() != null && getBoundingType().isGenerics();
	}

	protected boolean isWildcard() {
		return false;
	}

	@Override
	public boolean isSimplyQualified() {
		return false;
	}

	@Override
	@UnsettableProperty
	public CtTypeParameterReferenceImpl setSimplyQualified(boolean isSimplyQualified) {
		return this;
	}
}
