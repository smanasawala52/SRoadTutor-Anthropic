package com.sroadtutor.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/schools}. Only the OWNER calling this endpoint —
 * who must not already own a school — is allowed through; see
 * {@code SchoolService.createForCurrentOwner}.
 *
 * <p>Tax-ID and registration fields are optional and stored verbatim; format
 * validation is intentionally absent in V1 (province + entity-type permutations
 * are too varied to hand-roll without adding gotchas).</p>
 */
public record SchoolCreateRequest(
        @NotBlank @Size(max = 200) String name,

        /** SGI | ICBC | MTO | DMV. Defaults to SGI server-side if blank. */
        @Pattern(regexp = "^(SGI|ICBC|MTO|DMV)$",
                message = "jurisdiction must be one of SGI, ICBC, MTO, DMV")
        String jurisdiction,

        /** Two-letter Canadian province code. */
        @Pattern(regexp = "^[A-Z]{2}$",
                message = "province must be a 2-letter uppercase code")
        @Size(max = 8) String province,

        @Size(max = 40) String gstNumber,
        @Size(max = 40) String pstNumber,
        @Size(max = 40) String hstNumber,
        @Size(max = 80) String businessRegistrationNumber
) {}
