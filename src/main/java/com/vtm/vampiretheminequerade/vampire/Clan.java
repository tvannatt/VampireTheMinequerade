package com.vtm.vampiretheminequerade.vampire;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vtm.vampiretheminequerade.VampireTheMinequerade;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/** Every clan a vampire can belong to, plus the disciplines each one starts with.
 * Current Clans: Toreador, Ventrue, Gangrel, Brujah, Tremere
 * **/
public enum Clan {
    // Clan definitions. Maps a clan to its starting disciplines.
    TOREADOR("clan_toreador","d_celerity","d_auspex","d_presence"),
    VENTRUE("clan_ventrue","d_dominate","d_fortitude","d_presence"),
    GANGREL("clan_gangrel","d_protean","d_fortitude","d_animalism"),
    BRUJAH("clan_brujah","d_celerity","d_potence","d_presence"),
    TREMERE("clan_tremere","d_blood_sorcery","d_auspex","d_dominate");


    private final ResourceLocation id;
    private final Set<ResourceLocation> startingDisciplines;

    Clan(String path, String... disciplines) {
        this.id = ResourceLocation.fromNamespaceAndPath(VampireTheMinequerade.MODID, path);
        this.startingDisciplines = Arrays.stream(disciplines)
                .map(d -> ResourceLocation.fromNamespaceAndPath(VampireTheMinequerade.MODID, d))
                .collect(Collectors.toUnmodifiableSet());
    }

    public ResourceLocation id() {
        return id;
    }

    public Set<ResourceLocation> startingDisciplines() {
        return startingDisciplines;
    }

    public static Clan random(RandomSource random) {
        Clan[] all = values();
        return all[random.nextInt(all.length)];
    }

    public static Optional<Clan> byId(ResourceLocation id) {
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    public static Optional<Clan> byName(String name) {
        return Arrays.stream(values()).filter(c -> c.name().equalsIgnoreCase(name)).findFirst();
    }

    public static Stream<String> names() {
        return Arrays.stream(values()).map(c -> c.name().toLowerCase(Locale.ROOT));
    }
}
