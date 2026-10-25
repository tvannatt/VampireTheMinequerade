package com.vtm.vampiretheminequerade.Vampire;

import java.util.Optional;

public class BloodHandler {
     
    /* Update the player's blood pool. */
    public static Optional<VampireData> chgBlood(VampireData vData, int amount) {
        if (amount < 0 && vData.blood() + amount < 0) { return Optional.empty(); } 
        if (amount >= 0 && vData.blood() >= VampireData.BLOOD_MAX) { return Optional.empty(); } 
        return Optional.of(vData.withBlood(vData.blood() + amount));
    }
}
