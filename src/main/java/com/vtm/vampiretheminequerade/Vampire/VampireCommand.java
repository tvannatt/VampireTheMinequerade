package com.vtm.vampiretheminequerade.Vampire;

import java.util.Optional;
import java.util.function.UnaryOperator;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.vtm.vampiretheminequerade.VampireTheMinequerade;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;


// TODO: The info debug command should be replaced with a GUI that tells you your stats.

@EventBusSubscriber(modid = VampireTheMinequerade.MODID)
public class VampireCommand {

    /* Command boilerplate for Briagadier. */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vampire")
                .then(infoCommand())
                .then(turnPlayerCommand())
                .then(healCommand())
                .then(setBloodCommand())
                .then(setBloodPotencyCommand())
                .then(changeClanCommand())
                .then(removeVampirismCommand()));
    }

    private static boolean isOp(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> infoCommand() {
        return Commands.literal("info")
                .executes(VampireCommand::info);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> turnPlayerCommand() {
        return Commands.literal("turnplayer")
                .requires(VampireCommand::isOp)
                .executes(VampireCommand::turnSelf)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(VampireCommand::turnTarget));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> healCommand() {
        return Commands.literal("heal")
                .requires(VampireCommand::isOp)
                .executes(VampireCommand::healSelf)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(VampireCommand::healTarget));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> setBloodCommand() {
        return Commands.literal("setblood")
                .requires(VampireCommand::isOp)
                .then(Commands.argument("blood", IntegerArgumentType.integer())
                        .executes(VampireCommand::setBloodSelf)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(VampireCommand::setBloodTarget)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> setBloodPotencyCommand() {
        return Commands.literal("setbloodpotency")
                .requires(VampireCommand::isOp)
                .then(Commands.argument("bloodpotency", IntegerArgumentType.integer())
                        .executes(VampireCommand::setBloodPotencySelf)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(VampireCommand::setBloodPotencyTarget)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> changeClanCommand() {
        return Commands.literal("changeclan")
                .requires(VampireCommand::isOp)
                .then(Commands.argument("clan", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Clan.names(), builder))
                        .executes(VampireCommand::changeClanSelf)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(VampireCommand::changeClanTarget)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> removeVampirismCommand() {
        return Commands.literal("removevampirism")
                .requires(VampireCommand::isOp)
                .executes(VampireCommand::removeVampirismSelf)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(VampireCommand::removeVampirismTarget));
    }

    /** Returns current player vampire data. **/
    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        // Player has not been turned yet.
        Optional<VampireData> data = getVampire(player, ctx.getSource());
        if (data.isEmpty()) { return 0; }

        VampireData playerData = data.get();
        Component clanName = Component.translatable(playerData.clan().toLanguageKey("clan"));
        Component disciplineNames = ComponentUtils.formatList(playerData.disciplines(),
                discipline -> Component.translatable(discipline.toLanguageKey("discipline")));

        ctx.getSource().sendSuccess(() -> Component.literal("Current Clan: ").append(clanName)
                .append("\nBlood Level: "   + playerData.blood() + "\n" +
                        "Blood Potency: " + playerData.bloodPotency() + "\n" +
                        "Discipline(s): ").append(disciplineNames), false);
        return 1;
    }

    /** Give the player a random vampire clan at the lowest power level.
     *  If a target is not specified, turn the player instead.
     * **/
    private static int turn(CommandSourceStack source, ServerPlayer target) {
        // Prevent the command from issuing random data.
        if (target.hasData(ModAttachments.VAMPIRE_DATA)) {
            source.sendFailure(Component.literal(target.getName().getString() + " is already a vampire!"));
            return 0;
        }

        Clan clan = Clan.random(target.getRandom());
        target.setData(ModAttachments.VAMPIRE_DATA, VampireData.forClan(clan));
        source.sendSuccess(() -> Component.literal("Turned " + target.getName().getString() + " into a vampire (")
                .append(Component.translatable(clan.id().toLanguageKey("clan")))
                .append(")"), true);
        return 1;
    }

    /** Turn the command issuer into a vampire. **/
    private static int turnSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return turn(ctx.getSource(), ctx.getSource().getPlayerOrException());
    }

    /** Turn the specified target into a vampire. **/
    private static int turnTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return turn(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"));
    }

    /** Heal the player's vampire stats to full.
     *
     * TODO: This is blood for now, but we need to add damage types later (aggravated/superficial).
     *
     * **/
    private static int heal(CommandSourceStack source, ServerPlayer target) {
        if (!updateVampire(source, target, VampireData::withMaxBlood)) { return 0; }

        target.setHealth(target.getMaxHealth());
        return 1;
    }

    /** Heal the command issuer. **/
    private static int healSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return heal(ctx.getSource(), ctx.getSource().getPlayerOrException());
    }

    /** Heal the specified target. **/
    private static int healTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return heal(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"));
    }

    /** Set the player's blood to the given amount. **/
    private static int setBlood(CommandSourceStack source, ServerPlayer target, int blood) {
        if (blood < VampireData.BLOOD_MIN || blood > VampireData.BLOOD_MAX) {
            source.sendFailure(Component.literal(
                    "Blood must be between " + VampireData.BLOOD_MIN + " and " + VampireData.BLOOD_MAX));
            return 0;
        }

        return updateVampire(source, target, data -> data.withBlood(blood)) ? 1 : 0;
    }

    /** Set the command issuer's blood to the given amount. **/
    private static int setBloodSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setBlood(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                IntegerArgumentType.getInteger(ctx, "blood"));
    }

    /** Set the specified target's blood to the given amount. **/
    private static int setBloodTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setBlood(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"),
                IntegerArgumentType.getInteger(ctx, "blood"));
    }

    /** Set the player's blood potency to the given amount. **/
    private static int setBloodPotency(CommandSourceStack source, ServerPlayer target, int bloodPotency) {
        if (bloodPotency < VampireData.POTENCY_MIN || bloodPotency > VampireData.POTENCY_MAX) {
            source.sendFailure(Component.literal(
                    "Blood potency must be between " + VampireData.POTENCY_MIN + " and " + VampireData.POTENCY_MAX));
            return 0;
        }

        return updateVampire(source, target, data -> data.withBloodPotency(bloodPotency)) ? 1 : 0;
    }

    /** Set the command issuer's blood potency to the given amount. **/
    private static int setBloodPotencySelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setBloodPotency(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                IntegerArgumentType.getInteger(ctx, "bloodpotency"));
    }

    /** Set the specified target's blood potency to the given amount. **/
    private static int setBloodPotencyTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setBloodPotency(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"),
                IntegerArgumentType.getInteger(ctx, "bloodpotency"));
    }

    /** Change the player's clan. They lose their old disciplines and get the new clan's starting ones. **/
    private static int changeClan(CommandSourceStack source, ServerPlayer target, String name) {
        Optional<Clan> found = Clan.byName(name);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("Unknown clan: " + name));
            return 0;
        }

        Clan clan = found.get();
        if (!updateVampire(source, target, data -> data.withClan(clan.id()).withDisciplines(clan.startingDisciplines()))) {
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Changed " + target.getName().getString() + "'s clan to ")
                .append(Component.translatable(clan.id().toLanguageKey("clan"))), true);
        return 1;
    }

    /** Change the command issuer's clan. **/
    private static int changeClanSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return changeClan(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                StringArgumentType.getString(ctx, "clan"));
    }

    /** Change the specified target's clan. **/
    private static int changeClanTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return changeClan(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"),
                StringArgumentType.getString(ctx, "clan"));
    }

    /** Remove all vampire data from the player. **/
    private static int removeVampirism(CommandSourceStack source, ServerPlayer target) {
        if (getVampire(target, source).isEmpty()) { return 0; }

        target.removeData(ModAttachments.VAMPIRE_DATA);
        source.sendSuccess(() -> Component.literal("Removed vampirism from " + target.getName().getString()), true);
        return 1;
    }

    /** Remove vampirism from the command issuer. **/
    private static int removeVampirismSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return removeVampirism(ctx.getSource(), ctx.getSource().getPlayerOrException());
    }

    /** Remove vampirism from the specified target. **/
    private static int removeVampirismTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return removeVampirism(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"));
    }

    /** Get the player's vampire data without ever creating it. Tells the command issuer if they aren't a vampire. **/
    private static Optional<VampireData> getVampire(ServerPlayer player, CommandSourceStack source) {
        Optional<VampireData> data = player.getExistingData(ModAttachments.VAMPIRE_DATA);
        if (data.isEmpty()) {
            source.sendFailure(Component.literal("Not a vampire!"));
        }
        return data;
    }

    /** Replace the player's vampire data with a changed copy. Returns false if they aren't a vampire. **/
    private static boolean updateVampire(CommandSourceStack source, ServerPlayer player, UnaryOperator<VampireData> change) {
        Optional<VampireData> data = getVampire(player, source);
        if (data.isEmpty()) { return false; }

        player.setData(ModAttachments.VAMPIRE_DATA, change.apply(data.get()));
        return true;
    }

}
