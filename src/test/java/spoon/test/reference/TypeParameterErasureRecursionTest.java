package spoon.test.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;
import spoon.support.SerializationModelStreamer;

class TypeParameterErasureRecursionTest {
	@Test
	void genericAnonymousIteratorTraversalTerminates() {
		// contract: type-parameter erasure does not re-enter executable declaration lookup
		// (SPOON-ISSUE-PLACEHOLDER-02)
		// given
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource("src/test/resources/spoon/test/reference/TypeParameterErasureRecursion.java");

		// when
		var model = launcher.buildModel();

		// then
		assertThatCode(() -> model.getElements(element -> true).forEach(Object::toString)).doesNotThrowAnyException();
	}

	@Test
	void wildcardErasureUsesItsJavaLanguageBound() {
		// contract: wildcard erasure does not require a type-parameter declaration
		// (SPOON-ISSUE-PLACEHOLDER-02)
		// given
		Launcher launcher = new Launcher();
		CtWildcardReference wildcard = launcher.getFactory().Core().createWildcardReference();
		wildcard.setBoundingType(launcher.getFactory().Type().stringType());

		// when / then
		assertThat(wildcard.getTypeErasure().getQualifiedName()).isEqualTo("java.lang.String");
		assertThat(wildcard.setUpper(false).getTypeErasure().getQualifiedName()).isEqualTo("java.lang.Object");
	}

	@Test
	void adaptedExecutableTypeParameterResolvesToLexicalDeclaration() {
		// given
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource("src/test/resources/spoon/test/reference/TypeParameterErasureRecursion.java");

		// when
		var model = launcher.buildModel();
		CtInvocation<?> invocation = model.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("apply"))
				.findFirst()
				.orElseThrow();
		CtWildcardReference wildcard = (CtWildcardReference) invocation.getExecutable().getParameters().get(0);
		CtTypeParameterReference adaptedTypeParameter = (CtTypeParameterReference) wildcard.getBoundingType();

		// then
		assertThat(adaptedTypeParameter.getDeclaration().getParent(CtMethod.class).getSimpleName())
				.isEqualTo("mapAll");
	}

	@Test
	void nestedExecutableTypeParameterResolvesToCalleeDeclaration() {
		// given
		Launcher launcher = new Launcher();
		launcher.addInputResource(new VirtualFile("""
			class GenericProbe {
				<T extends Number> void callee(java.util.List<T> value) {}
				<T extends Number> void caller(java.util.List<T> value) { callee(value); }
			}
			""", "GenericProbe.java"));

		// when
		var model = launcher.buildModel();
		CtInvocation<?> invocation = model.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("callee"))
				.findFirst()
				.orElseThrow();
		CtTypeParameterReference nestedTypeParameter = (CtTypeParameterReference) invocation.getExecutable()
				.getParameters().get(0).getActualTypeArguments().get(0);

		// then
		assertThat(nestedTypeParameter.getDeclaration().getParent(CtMethod.class).getSimpleName())
				.isEqualTo("callee");
	}

	@Test
	void nestedDeclaringTypeParameterDoesNotResolveToShadowingCallerDeclaration() {
		// given
		Launcher launcher = new Launcher();
		launcher.addInputResource(new VirtualFile("""
			class GenericProbe<T> {
				void callee(java.util.List<T> value) {}
				<T> void caller() { callee(null); }
			}
			""", "GenericProbe.java"));

		// when
		var model = launcher.buildModel();
		CtInvocation<?> invocation = model.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("callee"))
				.findFirst()
				.orElseThrow();
		CtTypeParameterReference nestedTypeParameter = (CtTypeParameterReference) invocation.getExecutable()
				.getParameters().get(0).getActualTypeArguments().get(0);

		// then
		assertThat(nestedTypeParameter.getDeclaration())
				.isSameAs(model.getAllTypes().iterator().next().getFormalCtTypeParameters().get(0));
	}

	@Test
	void adaptedCallerTypeParameterDoesNotResolveToUnrelatedCalleeParameterWithSameName() {
		// given
		Launcher launcher = new Launcher();
		launcher.addInputResource(new VirtualFile("""
			class GenericProbe {
				<T extends CharSequence> void callee(java.util.List<T> value) {}
				<T extends Number> void caller(java.util.List<T> value) { callee(value); }
			}
			""", "GenericProbe.java"));
		var model = launcher.buildModel();
		CtMethod<?> caller = model.getAllTypes().iterator().next().getMethodsByName("caller").get(0);
		CtInvocation<?> invocation = caller.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("callee"))
				.findFirst()
				.orElseThrow();
		CtTypeParameterReference adaptedTypeParameter = caller.getFormalCtTypeParameters().get(0).getReference();
		invocation.getExecutable().getParameters().get(0).setActualTypeArguments(List.of(adaptedTypeParameter));

		// when
		var declaration = adaptedTypeParameter.getDeclaration();

		// then
		assertThat(declaration).isSameAs(caller.getFormalCtTypeParameters().get(0));
	}

	@Test
	void adaptedCallerTypeParameterOwnershipSurvivesCloneAndSerialization() throws Exception {
		// given
		Launcher launcher = new Launcher();
		launcher.addInputResource(new VirtualFile("""
			class GenericProbe {
				<T extends CharSequence> void callee(java.util.List<T> value) {}
				<T extends Number> void caller(java.util.List<T> value) { callee(value); }
			}
			""", "GenericProbe.java"));
		var model = launcher.buildModel();
		CtMethod<?> caller = model.getAllTypes().iterator().next().getMethodsByName("caller").get(0);
		CtInvocation<?> invocation = caller.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("callee"))
				.findFirst()
				.orElseThrow();
		CtTypeParameterReference adaptedTypeParameter = caller.getFormalCtTypeParameters().get(0).getReference();
		invocation.getExecutable().getParameters().get(0).setActualTypeArguments(List.of(adaptedTypeParameter));

		// when
		CtMethod<?> clonedCaller = caller.clone();
		CtInvocation<?> clonedInvocation = clonedCaller.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("callee"))
				.findFirst()
				.orElseThrow();
		CtTypeParameterReference clonedTypeParameter = (CtTypeParameterReference) clonedInvocation.getExecutable()
				.getParameters().get(0).getActualTypeArguments().get(0);

		ByteArrayOutputStream serialized = new ByteArrayOutputStream();
		SerializationModelStreamer streamer = new SerializationModelStreamer();
		streamer.save(launcher.getFactory(), serialized);
		Factory loadedFactory = streamer.load(new ByteArrayInputStream(serialized.toByteArray()));
		CtMethod<?> loadedCaller = loadedFactory.Type().get("GenericProbe").getMethodsByName("caller").get(0);
		CtInvocation<?> loadedInvocation = loadedCaller.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("callee"))
				.findFirst()
				.orElseThrow();
		CtTypeParameterReference loadedTypeParameter = (CtTypeParameterReference) loadedInvocation.getExecutable()
				.getParameters().get(0).getActualTypeArguments().get(0);

		// then
		assertThat(clonedTypeParameter.getDeclaration()).isSameAs(clonedCaller.getFormalCtTypeParameters().get(0));
		assertThat(loadedTypeParameter.getDeclaration()).isSameAs(loadedCaller.getFormalCtTypeParameters().get(0));
	}

	@Test
	void nestedConstructorTypeParameterResolvesToConstructorDeclaration() {
		// given
		Launcher launcher = new Launcher();
		launcher.addInputResource(new VirtualFile("""
			class GenericProbe {
				<T extends Number> GenericProbe(java.util.List<T> value) {}
				static <T extends Number> void caller(java.util.List<T> value) { new GenericProbe(value); }
			}
			""", "GenericProbe.java"));

		// when
		var model = launcher.buildModel();
		CtConstructorCall<?> constructorCall = model.getElements(new TypeFilter<>(CtConstructorCall.class)).stream()
				.findFirst()
				.orElseThrow();
		CtTypeParameterReference nestedTypeParameter = (CtTypeParameterReference) constructorCall.getExecutable()
				.getParameters().get(0).getActualTypeArguments().get(0);

		// then
		assertThat(nestedTypeParameter.getDeclaration().getParent()).isInstanceOf(CtConstructor.class);
	}

	@Test
	void deeplyNestedExecutableTypeParameterResolvesToCalleeDeclaration() {
		// given
		Launcher launcher = new Launcher();
		launcher.addInputResource(new VirtualFile("""
			class GenericProbe {
				<T extends Number> void callee(java.util.List<java.util.List<T>> value) {}
				<T extends Number> void caller(java.util.List<java.util.List<T>> value) { callee(value); }
			}
			""", "GenericProbe.java"));

		// when
		var model = launcher.buildModel();
		CtInvocation<?> invocation = model.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("callee"))
				.findFirst()
				.orElseThrow();
		CtTypeParameterReference nestedTypeParameter = (CtTypeParameterReference) invocation.getExecutable()
				.getParameters().get(0).getActualTypeArguments().get(0).getActualTypeArguments().get(0);

		// then
		assertThat(nestedTypeParameter.getDeclaration().getParent(CtMethod.class).getSimpleName())
				.isEqualTo("callee");
	}

	@Test
	void rawReceiverTypeParameterResolvesToDeclaringType() {
		// given
		Launcher launcher = new Launcher();
		launcher.addInputResource(new VirtualFile("""
			class Box<T> {
				void callee(java.util.List<java.util.List<T>> value) {}
			}
			class Caller<T> {
				void caller(Box box) { box.callee(null); }
			}
			""", "GenericProbe.java"));

		// when
		var model = launcher.buildModel();
		CtInvocation<?> invocation = model.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("callee"))
				.findFirst()
				.orElseThrow();
		CtTypeParameterReference nestedTypeParameter = (CtTypeParameterReference) invocation.getExecutable()
				.getParameters().get(0).getActualTypeArguments().get(0).getActualTypeArguments().get(0);

		// then
		assertThat(nestedTypeParameter.getDeclaration().getParent(CtType.class).getSimpleName()).isEqualTo("Box");
	}

	@Test
	void parameterizedReceiverTypeParameterResolvesToDeclaringType() {
		// given
		Launcher launcher = new Launcher();
		launcher.addInputResource(new VirtualFile("""
			class Box<T> {
				void callee(java.util.List<java.util.List<T>> value) {}
			}
			class Caller<T> {
				void caller(Box<T> box) { box.callee(null); }
			}
			""", "GenericProbe.java"));

		// when
		var model = launcher.buildModel();
		CtInvocation<?> invocation = model.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("callee"))
				.findFirst()
				.orElseThrow();
		CtTypeParameterReference nestedTypeParameter = (CtTypeParameterReference) invocation.getExecutable()
				.getParameters().get(0).getActualTypeArguments().get(0).getActualTypeArguments().get(0);

		// then
		assertThat(nestedTypeParameter.getDeclaration().getParent(CtType.class).getSimpleName()).isEqualTo("Box");
	}

	@Test
	void directGenericExecutableParameterResolvesToCalleeDeclaration() {
		// given
		Launcher launcher = new Launcher();
		launcher.addInputResource(new VirtualFile("""
			class GenericProbe {
				<T> void callee(T value) {}
				void caller() { callee(null); }
			}
			""", "GenericProbe.java"));

		// when
		var model = launcher.buildModel();
		CtInvocation<?> invocation = model.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("callee"))
				.findFirst()
				.orElseThrow();
		// then
		assertThat(invocation.getExecutable().getParameters().get(0).getQualifiedName())
				.isEqualTo("java.lang.Object");
		assertThat(invocation.getExecutable().getExecutableDeclaration().getSimpleName()).isEqualTo("callee");
	}

	@Test
	void siblingExecutableTypeParametersResolveRepeatedlyWithoutLeakingGuardState() {
		// given
		Launcher launcher = new Launcher();
		launcher.addInputResource(new VirtualFile("""
			class GenericProbe {
				<A, B> void callee(java.util.Map<A, java.util.List<B>> value) {}
				<A, B> void caller() { callee(null); }
			}
			""", "GenericProbe.java"));
		var model = launcher.buildModel();
		CtInvocation<?> invocation = model.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("callee"))
				.findFirst()
				.orElseThrow();
		CtTypeParameterReference first = (CtTypeParameterReference) invocation.getExecutable()
				.getParameters().get(0).getActualTypeArguments().get(0);
		CtTypeParameterReference second = (CtTypeParameterReference) invocation.getExecutable()
				.getParameters().get(0).getActualTypeArguments().get(1).getActualTypeArguments().get(0);

		// when / then
		for (int iteration = 0; iteration < 3; iteration++) {
			assertThat(first.getDeclaration().getParent(CtMethod.class).getSimpleName()).isEqualTo("callee");
			assertThat(second.getDeclaration().getParent(CtMethod.class).getSimpleName()).isEqualTo("callee");
		}

		// and when
		Launcher separateLauncher = new Launcher();
		separateLauncher.addInputResource(new VirtualFile("""
			class SeparateProbe {
				<T> void callee(java.util.List<T> value) {}
				<T> void caller() { callee(null); }
			}
			""", "SeparateProbe.java"));
		var separateModel = separateLauncher.buildModel();
		CtInvocation<?> separateInvocation = separateModel.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("callee"))
				.findFirst()
				.orElseThrow();
		CtTypeParameterReference separateTypeParameter = (CtTypeParameterReference) separateInvocation.getExecutable()
				.getParameters().get(0).getActualTypeArguments().get(0);

		// then
		assertThat(separateTypeParameter.getDeclaration().getParent(CtMethod.class).getSimpleName())
				.isEqualTo("callee");
	}
}
