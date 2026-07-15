package spoon.test.reference;

import ext.ImportedResource;
import ext.QualifiedOuter.Resource;

class AnonymousResourceProblemBinding {
	void useAnonymousResourceAsConstructorArgument() {
		try (
			var resource = new MissingResource() {
			};
			var wrapper = new MissingWrapper(resource)
		) {
			wrapper.consume();
		}
	}

	void useAnonymousResourceAsMessageArgument() {
		try (var resource = new MissingResource() {
		}) {
			consume(resource);
		}
	}

	void useParameterizedAnonymousResourceAsConstructorArgument() {
		try (
			var resource = new MissingResource<String>() {
			};
			var wrapper = new MissingWrapper(resource)
		) {
			wrapper.consumeParameterized();
		}
	}

	void useMembersOfAnonymousResource() {
		try (var resource = new MissingNode() {
		}) {
			var field = resource.missingField;
			resource.missingField.consume();
			var callback = resource.missingField::consume;
		}
	}

	void useImportedAnonymousResourceAsConstructorArgument() {
		try (
			var resource = new ImportedResource() {
			};
			var wrapper = new MissingImportedWrapper(resource)
		) {
			wrapper.consumeImported();
		}
	}

	void useNestedAnonymousResourceAsConstructorArgument() {
		try (
			var resource = new Outer<String>().new Resource<Integer>() {
			};
			var wrapper = new MissingNestedWrapper(resource)
		) {
			wrapper.consumeNested();
		}
	}

	void useDirectlyImportedNestedAnonymousResourceAsConstructorArgument() {
		try (
			var resource = new Resource() {
			};
			var wrapper = new MissingDirectNestedWrapper(resource)
		) {
			wrapper.consumeDirectNested();
		}
	}

	void useLowercaseEnclosingAnonymousResourceAsConstructorArgument() {
		try (
			var resource = new lower.Resource() {
			};
			var wrapper = new MissingLowercaseWrapper(resource)
		) {
			wrapper.consumeLowercase();
		}
	}

	class Outer<T> {
		class Resource<U> {
		}
	}

	class lower {
		class Resource {
		}
	}
}
