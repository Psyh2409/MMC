package org.mental_management_center.mmc.model;

public enum RoleBit {

        GUEST(1),        // 1 << 0
        READER(2),       // 1 << 1
        CLIENT(4),       // 1 << 2
        THERAPIST(8),    // 1 << 3
        ADMIN(16),       // 1 << 4
        TEST(128);             // 1 << 8

        private final int mask;

        RoleBit(int mask) {
            this.mask = mask;
        }

        public int getMask() {
            return mask;
        }
}
