/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2026 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) or the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */
package spoon.test.pattern;

import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtCase;
import spoon.reflect.code.CtSwitch;
import spoon.reflect.code.CtSwitchExpression;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.visitor.filter.TypeFilter;

import static org.assertj.core.api.Assertions.assertThat;

class GuardedColonSwitchPositionTest {
	private static CtModel createModel() {
		Launcher launcher = new Launcher();
		launcher.getEnvironment().setComplianceLevel(25);
		launcher.addInputResource("src/test/resources/spoon/test/pattern/GuardedColonSwitch.java");
		return launcher.buildModel();
	}

	@Test
	void guardedFallThroughCaseHasOrderedContainedSourcePosition() {
		// contract: SPOON-ISSUE-PLACEHOLDER-04 consecutive guarded colon cases have ordered, contained positions
		CtModel model = createModel();

		CtSwitch<?> ctSwitch = model.getElements(new TypeFilter<>(CtSwitch.class)).get(0);
		assertThat(ctSwitch.getCases()).hasSize(3);
		assertThat(ctSwitch.getCases().get(0).getGuard()).isNotNull();
		assertThat(ctSwitch.getCases().get(0).getStatements()).isEmpty();
		assertThat(ctSwitch.getCases().get(1).getGuard()).isNotNull();
		assertThat(ctSwitch.getCases().get(1).getCaseExpressions()).isNotEmpty();
		assertThat(ctSwitch.getCases().get(1).getStatements()).hasSize(2);
		for (CtCase<?> ctCase : ctSwitch.getCases()) {
			SourcePosition position = ctCase.getPosition();
			assertThat(position.isValidPosition()).isTrue();
			assertThat(position.getSourceStart()).isLessThanOrEqualTo(position.getSourceEnd());
			assertThat(position.getSourceStart()).isGreaterThanOrEqualTo(ctSwitch.getPosition().getSourceStart());
			assertThat(position.getSourceEnd()).isLessThanOrEqualTo(ctSwitch.getPosition().getSourceEnd());
		}
	}

	@Test
	void castGuardIsAttachedToCase() {
		// contract: guard identification follows JDT through casts that Spoon flattens into the expression
		CtModel model = createModel();

		CtSwitchExpression<?, ?> ctSwitch = model.getElements(new TypeFilter<>(CtSwitchExpression.class)).get(0);
		CtCase<?> guardedCase = ctSwitch.getCases().get(0);
		assertThat(guardedCase.getGuard()).isNotNull();
		assertThat(guardedCase.getGuard().getTypeCasts()).hasSize(1);
	}
}
