package spoon.test.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;

class ProblemReferenceBindingSiblingTest {
	@Test
	void conditionalAnonymousResourceCanBeUsedAsAConstructorArgument() {
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource(new VirtualFile("""
				class ConditionalResource {
					boolean selectFirst;
					void use() {
						try (
							var resource = new MissingResource() {};
							var wrapper = new MissingConditionalWrapper(selectFirst ? resource : resource)
						) {
							wrapper.consume();
						}
					}
				}
				""", "ConditionalResource.java"));

		var model = launcher.buildModel();

		CtConstructorCall<?> wrapper = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class)).stream()
				.filter(call -> call.getType().getSimpleName().equals("MissingConditionalWrapper"))
				.findFirst()
				.orElseThrow();
		assertThat(wrapper.getExecutable().getParameters().get(0).getSimpleName())
				.isEqualTo("MissingResource");
	}
}
