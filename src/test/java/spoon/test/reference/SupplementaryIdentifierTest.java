/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2026 INRIA and contributors
 */
package spoon.test.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import spoon.JLSViolation;
import spoon.Launcher;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;

class SupplementaryIdentifierTest {

	private static final String SUPPLEMENTARY_IDENTIFIER = "\uD801\uDC00abc";

	@Test
	void acceptsRawSupplementaryIdentifierReferences() {
		// contract: valid supplementary Java identifiers survive declaration and reference creation
		// (SPOON-ISSUE-PLACEHOLDER-06)
		CtClass<?> type = Launcher.parseClass("""
				class SupplementaryIdentifier {
					int %s = 1;
					boolean valid() { return %s == 1; }
				}
				""".formatted(SUPPLEMENTARY_IDENTIFIER, SUPPLEMENTARY_IDENTIFIER));

		CtFieldRead<?> fieldRead = type.getElements(new TypeFilter<>(CtFieldRead.class)).get(0);

		assertThat(fieldRead.getVariable().getSimpleName()).isEqualTo(SUPPLEMENTARY_IDENTIFIER);
	}

	@Test
	void acceptsEscapedSupplementaryIdentifierReferences() {
		// contract: Unicode escapes representing a valid supplementary identifier are validated as one code point
		// (SPOON-ISSUE-PLACEHOLDER-06)
		CtClass<?> type = Launcher.parseClass("""
				class SupplementaryIdentifier {
					int \\uD801\\uDC00abc = 1;
					boolean valid() { return \\uD801\\uDC00abc == 1; }
				}
				""");

		CtFieldRead<?> fieldRead = type.getElements(new TypeFilter<>(CtFieldRead.class)).get(0);

		assertThat(fieldRead.getVariable().getSimpleName()).isEqualTo(SUPPLEMENTARY_IDENTIFIER);
	}

	@Test
	void rejectsSupplementaryCodePointsThatAreNotJavaIdentifierParts() {
		// contract: code-point validation does not make arbitrary supplementary characters legal identifiers
		// (SPOON-ISSUE-PLACEHOLDER-06)
		var reference = new Launcher().getFactory().Core().createTypeReference();

		assertThatThrownBy(() -> reference.setSimpleName("\uD83D\uDE00name"))
				.isInstanceOf(JLSViolation.class);
	}
}
