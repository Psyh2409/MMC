package org.mental_management_center.mmc.model;

public enum RoleBit {

        GUEST((byte) 1),        // 0000 0001 (1)  — Просто зайшов подивитись
        READER((byte) 2),       // 0000 0010 (2)  — Підписався на контент
        CLIENT((byte) 4),       // 0000 0100 (4)  — Твій пацієнт/клієнт
        THERAPIST((byte) 8),    // 0000 1000 (8)  — Колега, фахівець
        ADMIN((byte) 16);       // 0001 0000 (16) — Ти, господар системи

        private final byte mask;

        RoleBit(byte mask) {
            this.mask = mask;
        }

        public byte getMask() {
            return mask;
        }
}
