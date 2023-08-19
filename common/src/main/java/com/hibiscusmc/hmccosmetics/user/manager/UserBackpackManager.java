package com.hibiscusmc.hmccosmetics.user.manager;

import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBackpackType;
import com.hibiscusmc.hmccosmetics.hooks.Hooks;
import com.hibiscusmc.hmccosmetics.hooks.modelengine.MegEntityWrapper;
import com.hibiscusmc.hmccosmetics.nms.NMSHandlers;
import com.hibiscusmc.hmccosmetics.nms.PacketArmorStand;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.PlayerUtils;
import com.hibiscusmc.hmccosmetics.util.packets.PacketManager;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Level;

public class UserBackpackManager {

    private boolean hideBackpack;
    private PacketArmorStand invisibleArmorStand;
    private ArrayList<Integer> particleCloud = new ArrayList<>();
    private final CosmeticUser user;
    @Getter
    private UserBackpackCloudManager cloudManager;

    public UserBackpackManager(CosmeticUser user) {
        this.user = user;
        this.hideBackpack = false;
        this.cloudManager = new UserBackpackCloudManager(user.getUniqueId());
    }

    public int getFirstArmorStandId() {
        return invisibleArmorStand.getEntityId();
    }

    public PacketArmorStand getArmorStand() {
        return invisibleArmorStand;
    }

    public boolean IsValidBackpackEntity() {
        if (invisibleArmorStand == null) {
            MessagesUtil.sendDebugMessages("InvisibleArmorStand is Null!");
            return false;
        }
        return true;
    }

    public void spawnBackpack(CosmeticBackpackType cosmeticBackpackType) {
        MessagesUtil.sendDebugMessages("spawnBackpack Bukkit - Start");

        spawn(cosmeticBackpackType);
    }

    private void spawn(CosmeticBackpackType cosmeticBackpackType) {
        if (this.invisibleArmorStand != null) return;
        boolean firstPerson = false;
        Entity entity = user.getEntity();

        this.invisibleArmorStand = NMSHandlers.getHandler().spawnBackpack(
                user,
                cosmeticBackpackType,
                new HashSet<>(PlayerUtils.getNearbyPlayers(entity.getLocation()))
        );

        int[] passengerIDs = new int[entity.getPassengers().size() + 1];

        for (int i = 0; i < entity.getPassengers().size(); i++) {
            passengerIDs[i] = entity.getPassengers().get(i).getEntityId();
        }

        passengerIDs[passengerIDs.length - 1] = this.getFirstArmorStandId();

        List<Player> outsideViewers = user.getUserBackpackManager().getCloudManager().refreshViewers(user.getEntity().getLocation());
        PacketManager.sendRidingPacket(user.getEntity().getEntityId(), passengerIDs, outsideViewers);

        ArrayList<Player> owner = new ArrayList<>();
        if (user.getPlayer() != null) owner.add(user.getPlayer());

        if (cosmeticBackpackType.isFirstPersonCompadible()) {
            firstPerson = true;
            for (int i = particleCloud.size(); i < cosmeticBackpackType.getHeight(); i++) {
                int entityId = NMSHandlers.getHandler().getNextEntityId();
                PacketManager.sendEntitySpawnPacket(user.getEntity().getLocation(), entityId, EntityType.AREA_EFFECT_CLOUD, UUID.randomUUID());
                PacketManager.sendCloudEffect(entityId, PacketManager.getViewers(user.getEntity().getLocation()));
                this.particleCloud.add(entityId);
            }
            // Copied code from updating the backpack
            for (int i = 0; i < particleCloud.size(); i++) {
                if (i == 0) PacketManager.sendRidingPacket(entity.getEntityId(), particleCloud.get(i), owner);
                else PacketManager.sendRidingPacket(particleCloud.get(i - 1), particleCloud.get(i) , owner);
            }
            PacketManager.sendRidingPacket(particleCloud.get(particleCloud.size() - 1), user.getUserBackpackManager().getFirstArmorStandId(), owner);
        }
        if (!user.getHidden()) {
            if (firstPerson) {
                NMSHandlers.getHandler().equipmentSlotUpdate(user.getUserBackpackManager().getFirstArmorStandId(), EquipmentSlot.HEAD, user.getUserCosmeticItem(cosmeticBackpackType, cosmeticBackpackType.getFirstPersonBackpack()), owner);
            } else {
                NMSHandlers.getHandler().equipmentSlotUpdate(user.getUserBackpackManager().getFirstArmorStandId(), EquipmentSlot.HEAD, user.getUserCosmeticItem(cosmeticBackpackType), owner);
            }
        } else {
            NMSHandlers.getHandler().equipmentSlotUpdate(user.getUserBackpackManager().getFirstArmorStandId(), EquipmentSlot.HEAD, new ItemStack(Material.AIR), owner);
        }
        PacketManager.sendRidingPacket(entity.getEntityId(), passengerIDs, outsideViewers);

        // No one should be using ME because it barely works but some still use it, so it's here
        if (cosmeticBackpackType.getModelName() != null && Hooks.isActiveHook("ModelEngine")) {
            if (ModelEngineAPI.api.getModelRegistry().getBlueprint(cosmeticBackpackType.getModelName()) == null) {
                MessagesUtil.sendDebugMessages("Invalid Model Engine Blueprint " + cosmeticBackpackType.getModelName(), Level.SEVERE);
                return;
            }
            final ModeledEntity modeledEntity;
            final MegEntityWrapper wrapper = this.invisibleArmorStand.getMegEntityWrapper();
            if (ModelEngineAPI.isModeledEntity(wrapper.entity().getUniqueId())) {
                modeledEntity = ModelEngineAPI.getModeledEntity(wrapper.entity().getUniqueId());
            } else {
                modeledEntity = ModelEngineAPI.createModeledEntity(wrapper.entity());
            }
            ActiveModel model = ModelEngineAPI.createActiveModel(ModelEngineAPI.getBlueprint(cosmeticBackpackType.getModelName()));
            model.setCanHurt(false);
            modeledEntity.addModel(model, false);
        }

        MessagesUtil.sendDebugMessages("spawnBackpack Bukkit - Finish");
    }

    public void despawnBackpack() {
        if (invisibleArmorStand != null) {
            invisibleArmorStand.despawn();
            this.invisibleArmorStand = null;
        }
        if (particleCloud != null) {
            for (Integer entityId : particleCloud) {
                PacketManager.sendEntityDestroyPacket(entityId, getCloudManager().getViewers());
            }
            this.particleCloud = null;
        }
    }

    public void hideBackpack() {
        if (user.getHidden()) return;
        getArmorStand().getEquipment().clear();
        hideBackpack = true;
    }

    public void showBackpack() {
        if (!hideBackpack) return;
        CosmeticBackpackType cosmeticBackpackType = (CosmeticBackpackType) user.getCosmetic(CosmeticSlot.BACKPACK);
        ItemStack item = user.getUserCosmeticItem(cosmeticBackpackType);
        getArmorStand().setHelmet(item);
        hideBackpack = false;
    }

    public void setVisibility(boolean shown) {
        hideBackpack = shown;
    }

    public ArrayList<Integer> getAreaEffectEntityId() {
        return particleCloud;
    }

    public void setItem(ItemStack item) {
            getArmorStand().setHelmet(item);
    }

    public void clearItems() {
        ItemStack item = new ItemStack(Material.AIR);
        getArmorStand().setHelmet(item);
    }

}
