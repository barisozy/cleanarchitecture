package com.barisozy.cleanarchitecture.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void derivesThePageCountAndTheNextFlag() {
        PageResult<String> empty = new PageResult<>(List.of(), 0, 20, 0);
        assertThat(empty.totalPages()).isZero();
        assertThat(empty.hasNext()).isFalse();

        PageResult<String> first = new PageResult<>(List.of("a", "b"), 0, 2, 5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();

        PageResult<String> last = new PageResult<>(List.of("e"), 2, 2, 5);
        assertThat(last.hasNext()).isFalse();
    }

    @Test
    void rejectsAnInvalidPaginationContract() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PageResult<>(List.of(), -1, 20, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new PageResult<>(List.of(), 0, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new PageResult<>(List.of(), 0, 20, -1));
    }
}
