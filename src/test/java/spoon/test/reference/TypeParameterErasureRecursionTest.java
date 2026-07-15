package spoon.test.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.reflect.reference.CtWildcardReference;

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
}
