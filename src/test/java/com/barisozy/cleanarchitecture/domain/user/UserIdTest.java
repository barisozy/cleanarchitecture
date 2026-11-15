package com.barisozy.cleanarchitecture.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barisozy.cleanarchitecture.domain.DomainException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class UserIdTest {

    private static final String CANONICAL_UUID = "11111111-1111-1111-1111-111111111111";

    @Test
    void createsParsesAndValidatesTheIdentity() {
        UserId id = UserId.newId();
        assertThat(id.value()).isNotNull();
        assertThat(UserId.newId()).isNotEqualTo(id);

        assertThat(UserId.from(CANONICAL_UUID).value()).isEqualTo(UUID.fromString(CANONICAL_UUID));

        assertThatThrownBy(() -> UserId.from("not-a-uuid"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must be a UUID");

        @Nullable UUID value = null;
        assertThatThrownBy(() -> new UserId(value)).isInstanceOf(DomainException.class);
    }
}
