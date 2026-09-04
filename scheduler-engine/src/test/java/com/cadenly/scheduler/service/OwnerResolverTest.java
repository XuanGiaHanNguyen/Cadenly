package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.OwnerResolution;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerResolverTest {

    private final OwnerResolver resolver = new OwnerResolver(new UserDirectoryService());

    @Test
    void resolvesExactKnownName() {
        OwnerResolution result = resolver.resolve("John");
        assertThat(result.isResolved()).isTrue();
        assertThat(result.ownerId()).isEqualTo(UserDirectoryService.JOHN_ID);
    }

    @Test
    void twoNameVariantsForSamePersonResolveToTheSameUuid() {
        OwnerResolution short_ = resolver.resolve("Sarah");
        OwnerResolution full = resolver.resolve("Sarah Kim");

        assertThat(short_.ownerId()).isEqualTo(UserDirectoryService.SARAH_ID);
        assertThat(full.ownerId()).isEqualTo(UserDirectoryService.SARAH_ID);
        assertThat(short_.ownerId()).isEqualTo(full.ownerId());
    }

    @Test
    void matchingIsCaseAndWhitespaceInsensitive() {
        OwnerResolution result = resolver.resolve("  SARAH kim  ");
        assertThat(result.ownerId()).isEqualTo(UserDirectoryService.SARAH_ID);
    }

    @Test
    void emptyStringIsUnassignedWithNoOwnerStatedReason() {
        OwnerResolution result = resolver.resolve("");
        assertThat(result.isResolved()).isFalse();
        assertThat(result.unresolvedReason()).isEqualTo("no owner stated");
    }

    @Test
    void blocklistedPlaceholderPhrasesAreUnassigned_notTreatedAsRealNames() {
        // literal strings observed from real local-LLM output in Phase 6 testing
        for (String placeholder : new String[]{"Unknown", "someone", "Someone", "no owner mentioned", "Unassigned"}) {
            OwnerResolution result = resolver.resolve(placeholder);
            assertThat(result.isResolved())
                    .as("expected '%s' to be treated as unassigned", placeholder)
                    .isFalse();
            assertThat(result.unresolvedReason()).isEqualTo("no owner stated");
        }
    }

    @Test
    void unrecognizedRealLookingNameIsUnassigned_withADistinctReasonFromNoOwnerStated() {
        OwnerResolution result = resolver.resolve("Bob");
        assertThat(result.isResolved()).isFalse();
        assertThat(result.unresolvedReason()).contains("Bob").contains("not found in directory");
        assertThat(result.unresolvedReason()).isNotEqualTo("no owner stated");
    }
}
