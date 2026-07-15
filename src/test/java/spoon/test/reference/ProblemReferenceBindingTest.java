package spoon.test.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.visitor.filter.TypeFilter;

class ProblemReferenceBindingTest {
	@Test
	void anonymousResourceProblemBindingUsesItsDeclaredTypeName() {
		// contract: unresolved anonymous resources use a legal type name for later invocations
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource("src/test/resources/spoon/test/reference/ProblemReferenceBinding.java");

		// when
		var model = launcher.buildModel();

		// then
		CtInvocation<?> invocation = invocationNamed(model, "call");
		assertThat(invocation.getTarget()).isInstanceOf(CtVariableRead.class);
		CtVariableRead<?> variableRead = (CtVariableRead<?>) invocation.getTarget();
		assertThat(variableRead.getVariable().getType().getSimpleName()).isEqualTo("MissingResource");
	}

	@Test
	void parameterizedAnonymousResourceDropsTypeArgumentsFromItsProblemBinding() {
		// contract: wrapped problem-binding type arguments never become part of a simple name
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForProblemReferenceBinding();

		// when
		var model = launcher.buildModel();

		// then
		CtVariableRead<?> variableRead = (CtVariableRead<?>) invocationNamed(model, "parameterizedCall").getTarget();
		assertThat(variableRead.getVariable().getType().getSimpleName()).isEqualTo("MissingResource");
	}

	@Test
	void unresolvedExecutableUsesTheRecoveredResourceType() {
		// contract: executable references reuse recovered inferred-resource evidence
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForProblemReferenceBinding();

		// when
		var model = launcher.buildModel();

		// then
		CtInvocation<?> invocation = invocationNamed(model, "configure");
		assertThat(invocation.getExecutable().getDeclaringType().getSimpleName()).isEqualTo("MissingService");
	}

	@Test
	void nestedAnonymousResourcePreservesItsDeclaringType() {
		// contract: a nested resource problem binding represents the nested and declaring names separately
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForProblemReferenceBinding();

		// when
		var model = launcher.buildModel();

		// then
		CtVariableRead<?> variableRead = (CtVariableRead<?>) invocationNamed(model, "nestedCall").getTarget();
		assertThat(variableRead.getVariable().getType().getSimpleName()).isEqualTo("Resource");
		assertThat(variableRead.getVariable().getType().getDeclaringType().getSimpleName()).isEqualTo("Outer");
		assertThat(variableRead.getVariable().getType().getQualifiedName()).isEqualTo("spoon.test.reference.Outer$Resource");
	}

	@Test
	void nestedParameterizedAnonymousResourcePreservesEveryTypeSegment() {
		// contract: erasing nested type arguments does not discard later nested type names
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForProblemReferenceBinding();

		// when
		var model = launcher.buildModel();

		// then
		CtVariableRead<?> variableRead = (CtVariableRead<?>) invocationNamed(model, "nestedGenericCall").getTarget();
		assertThat(variableRead.getVariable().getType().getSimpleName()).isEqualTo("Resource");
		assertThat(variableRead.getVariable().getType().getDeclaringType().getSimpleName()).isEqualTo("GenericOuter");
	}

	@Test
	void genericEnclosingResourceDropsTypeArgumentsBeforeResolvingItsNestedType() {
		// contract: enclosing-only type arguments are erased without discarding the nested resource
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForProblemReferenceBinding();

		// when
		var model = launcher.buildModel();

		// then
		CtVariableRead<?> variableRead = (CtVariableRead<?>) invocationNamed(model, "genericEnclosingCall").getTarget();
		assertThat(variableRead.getVariable().getType().getSimpleName()).isEqualTo("PlainResource");
		assertThat(variableRead.getVariable().getType().getDeclaringType().getSimpleName()).isEqualTo("GenericOuter");
	}

	@Test
	void importedAnonymousResourceRetainsItsImportedPackage() {
		// contract: normalization happens before resolving an imported resource name
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForProblemReferenceBinding();

		// when
		var model = launcher.buildModel();

		// then
		CtVariableRead<?> variableRead = (CtVariableRead<?>) invocationNamed(model, "importedCall").getTarget();
		assertThat(variableRead.getVariable().getType().getQualifiedName()).isEqualTo("ext.ImportedResource");
	}

	@Test
	void packageQualifiedNestedResourceUsesOnePackageSeparator() {
		// contract: package-qualified nested resources separate package and declaring types exactly once
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForProblemReferenceBinding();

		// when
		var model = launcher.buildModel();

		// then
		CtVariableRead<?> variableRead = (CtVariableRead<?>) invocationNamed(model, "qualifiedNestedCall").getTarget();
		assertThat(variableRead.getVariable().getType().getQualifiedName()).isEqualTo("ext.QualifiedOuter$Resource");
	}

	@Test
	void lowercaseDeclaringTypeIsNotTreatedAsAPackage() {
		// contract: source binding evidence takes precedence over capitalization conventions
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForProblemReferenceBinding();

		// when
		var model = launcher.buildModel();

		// then
		CtVariableRead<?> variableRead = (CtVariableRead<?>) invocationNamed(model, "lowercaseNestedCall").getTarget();
		assertThat(variableRead.getVariable().getType().getDeclaringType().getSimpleName()).isEqualTo("lower");
	}

	@Test
	void explicitResourceRetainsItsDeclaredType() {
		// contract: anonymous-initializer recovery applies only to inferred resource declarations
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForProblemReferenceBinding();

		// when
		var model = launcher.buildModel();

		// then
		CtVariableRead<?> variableRead = (CtVariableRead<?>) invocationNamed(model, "baseCall").getTarget();
		assertThat(variableRead.getVariable().getType().getSimpleName()).isEqualTo("BaseResource");
	}

	@Test
	void anonymousResourceCanBeUsedAsAConstructorArgument() {
		// contract: inferred anonymous resource bindings remain legal when reused as argument types
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForAnonymousResourceProblemBinding();

		// when
		var model = launcher.buildModel();

		// then
		assertThat(model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class)))
				.filteredOn(call -> call.getType().getSimpleName().equals("MissingWrapper"))
				.extracting(call -> call.getExecutable().getParameters().get(0).getSimpleName())
				.containsOnly("MissingResource");
	}

	@Test
	void anonymousResourceCanBeUsedAsAMessageArgument() {
		// contract: inferred anonymous resource bindings are recovered before bound method parameters
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForAnonymousResourceProblemBinding();

		// when
		var model = launcher.buildModel();

		// then
		CtInvocation<?> invocation = model.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals("consume"))
				.filter(candidate -> candidate.getArguments().size() == 1)
				.findFirst()
				.orElseThrow();
		assertThat(invocation.getExecutable().getParameters())
				.singleElement()
				.extracting(spoon.reflect.reference.CtTypeReference::getSimpleName)
				.isEqualTo("MissingResource");
	}

	@Test
	void anonymousResourceCanDeclareReferencedFields() {
		// contract: inferred anonymous resource bindings remain legal as field declaring types
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForAnonymousResourceProblemBinding();

		// when
		var model = launcher.buildModel();

		// then
		assertThat(model.getElements(new TypeFilter<CtFieldRead<?>>(CtFieldRead.class)))
				.filteredOn(read -> read.getVariable().getSimpleName().equals("missingField"))
				.isNotEmpty()
				.allSatisfy(read -> assertThat(read.getVariable().getDeclaringType().getSimpleName()).isEqualTo("MissingNode"));
	}

	@Test
	void importedAnonymousResourceCanBeUsedAsAConstructorArgument() {
		// contract: normalized resource names retain their explicit import
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForAnonymousResourceProblemBinding();

		// when
		var model = launcher.buildModel();

		// then
		assertThat(constructorParameter(model, "MissingImportedWrapper").getQualifiedName())
				.isEqualTo("ext.ImportedResource");
	}

	@Test
	void nestedGenericAnonymousResourceRetainsEveryTypeSegmentAsAConstructorArgument() {
		// contract: erasing type arguments preserves nested resource names and declaring types
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForAnonymousResourceProblemBinding();

		// when
		var model = launcher.buildModel();

		// then
		var parameter = constructorParameter(model, "MissingNestedWrapper");
		assertThat(parameter.getSimpleName()).isEqualTo("Resource");
		assertThat(parameter.getDeclaringType().getSimpleName()).isEqualTo("Outer");
	}

	@Test
	void directlyImportedNestedAnonymousResourceRetainsItsPackageAsAConstructorArgument() {
		// contract: direct nested-type imports resolve the normalized type chain
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForAnonymousResourceProblemBinding();

		// when
		var model = launcher.buildModel();

		// then
		assertThat(constructorParameter(model, "MissingDirectNestedWrapper").getQualifiedName())
				.isEqualTo("ext.QualifiedOuter$Resource");
	}

	@Test
	void lowercaseEnclosingAnonymousResourceRemainsATypeAsAConstructorArgument() {
		// contract: a source-declared lowercase enclosing type is not interpreted as a package
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = launcherForAnonymousResourceProblemBinding();

		// when
		var model = launcher.buildModel();

		// then
		var parameter = constructorParameter(model, "MissingLowercaseWrapper");
		assertThat(parameter.getSimpleName()).isEqualTo("Resource");
		assertThat(parameter.getDeclaringType().getSimpleName()).isEqualTo("lower");
	}

	@Test
	void preJavaTenTypeNamedVarRetainsItsDeclaredType() {
		// contract: `var` recovery is disabled while `var` remains an ordinary type name
		// (SPOON-ISSUE-PLACEHOLDER-01)
		// given
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setComplianceLevel(9);
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource("src/test/resources/spoon/test/reference/PreJavaTenVarType.java");

		// when
		var model = launcher.buildModel();

		// then
		CtVariableRead<?> variableRead = (CtVariableRead<?>) invocationNamed(model, "varTypeCall").getTarget();
		assertThat(variableRead.getVariable().getType().getSimpleName()).isEqualTo("var");
	}

	private static Launcher launcherForProblemReferenceBinding() {
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource("src/test/resources/spoon/test/reference/ProblemReferenceBinding.java");
		launcher.addInputResource("src/test/resources/spoon/test/reference/issue01");
		return launcher;
	}

	private static Launcher launcherForAnonymousResourceProblemBinding() {
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource("src/test/resources/spoon/test/reference/AnonymousResourceProblemBinding.java");
		launcher.addInputResource("src/test/resources/spoon/test/reference/issue01");
		return launcher;
	}

	private static spoon.reflect.reference.CtTypeReference<?> constructorParameter(
			spoon.reflect.CtModel model, String constructedType) {
		return model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class)).stream()
				.filter(call -> call.getType().getSimpleName().equals(constructedType))
				.findFirst()
				.orElseThrow()
				.getExecutable()
				.getParameters()
				.get(0);
	}

	private static CtInvocation<?> invocationNamed(spoon.reflect.CtModel model, String name) {
		return model.getElements(new TypeFilter<>(CtInvocation.class)).stream()
				.filter(candidate -> candidate.getExecutable().getSimpleName().equals(name))
				.findFirst()
				.orElseThrow();
	}
}
