package com.sroadtutor.phone.model;

/**
 * The four mutually-exclusive owner kinds enforced by the {@code phone_owner_exactly_one}
 * CHECK constraint on {@code phone_numbers}. Q1a — there is intentionally no
 * {@code PARENT} owner: parents are users with role=PARENT, so their phones use
 * {@link #USER}.
 *
 * <p>Wire format: the value the SPA sends in the {@code ownerType} field of every
 * phone-number request.</p>
 */
public enum PhoneOwnerType {
    USER,
    SCHOOL,
    INSTRUCTOR,
    STUDENT
}
