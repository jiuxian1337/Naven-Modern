package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.google.common.collect.Lists;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventShader;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.SmoothAnimationTimer;
import com.heypixel.heypixelmod.obsoverlay.utils.StencilUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.text.CustomTextRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.MobEffectTextureManager;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.joml.Vector4f;

@ModuleInfo(
   name = "EffectDisplay",
   description = "Displays potion effects on the HUD",
   category = Category.RENDER
)
public class EffectDisplay extends Module {
   private List<Runnable> list;
   private final Map<MobEffect, EffectDisplay.MobEffectInfo> infos = new ConcurrentHashMap<>();
   private final Color headerColor = new Color(150, 45, 45, 255);
   private final Color bodyColor = new Color(0, 0, 0, 50);
   private final List<Vector4f> blurMatrices = new ArrayList<>();

   @EventTarget(4)
   public void renderIcons(EventRender2D e) {
      this.list.forEach(Runnable::run);
   }

   @EventTarget
   public void onShader(EventShader e) {
      for (Vector4f matrix : this.blurMatrices) {
         RenderUtils.drawRoundedRect(e.getStack(), matrix.x(), matrix.y(), matrix.z(), matrix.w(), 5.0F, 1073741824);
      }
   }

   @EventTarget
   public void onRender(EventRender2D e) {
      for (MobEffectInstance effect : mc.player.getActiveEffects()) {
         EffectDisplay.MobEffectInfo info;
         if (this.infos.containsKey(effect.getEffect())) {
            info = this.infos.get(effect.getEffect());
         } else {
            info = new EffectDisplay.MobEffectInfo();
            this.infos.put(effect.getEffect(), info);
         }

         info.maxDuration = Math.max(info.maxDuration, effect.getDuration());
         info.duration = effect.getDuration();
         info.amplifier = effect.getAmplifier();
         info.shouldDisappear = false;
      }

      int startY = mc.getWindow().getGuiScaledHeight() / 2 - this.infos.size() * 16;
      this.list = Lists.newArrayListWithExpectedSize(this.infos.size());
      this.blurMatrices.clear();

      for (Entry<MobEffect, EffectDisplay.MobEffectInfo> entry : this.infos.entrySet()) {
         e.getStack().pushPose();
         EffectDisplay.MobEffectInfo effectInfo = entry.getValue();
         String text = this.getDisplayName(entry.getKey(), effectInfo);
         if (effectInfo.yTimer.value == -1.0F) {
            effectInfo.yTimer.value = (float)startY;
         }

         CustomTextRenderer harmony = Fonts.harmony;
         effectInfo.width = 25.0F + harmony.getWidth(text, 0.3) + 20.0F;
         float x = effectInfo.xTimer.value;
         float y = effectInfo.yTimer.value;
         effectInfo.shouldDisappear = !mc.player.hasEffect(entry.getKey());
         if (effectInfo.shouldDisappear) {
            effectInfo.xTimer.target = -effectInfo.width - 20.0F;
            if (x <= -effectInfo.width - 20.0F) {
               this.infos.remove(entry.getKey());
            }
         } else {
            effectInfo.durationTimer.target = (float)effectInfo.duration / (float)effectInfo.maxDuration * effectInfo.width;
            if (effectInfo.durationTimer.value <= 0.0F) {
               effectInfo.durationTimer.value = effectInfo.durationTimer.target;
            }

            effectInfo.xTimer.target = 10.0F;
            effectInfo.yTimer.target = (float)startY;
            effectInfo.yTimer.update(true);
         }

         effectInfo.durationTimer.update(true);
         effectInfo.xTimer.update(true);
         StencilUtils.write(false);
         this.blurMatrices.add(new Vector4f(x + 2.0F, y + 2.0F, effectInfo.width - 2.0F, 28.0F));
         RenderUtils.drawRoundedRect(e.getStack(), x + 2.0F, y + 2.0F, effectInfo.width - 2.0F, 28.0F, 5.0F, -1);
         StencilUtils.erase(true);
         RenderUtils.fillBound(e.getStack(), x, y, effectInfo.width, 30.0F, this.bodyColor.getRGB());
         RenderUtils.fillBound(e.getStack(), x, y, effectInfo.durationTimer.value, 30.0F, this.bodyColor.getRGB());
         RenderUtils.drawRoundedRect(e.getStack(), x + effectInfo.width - 10.0F, y + 7.0F, 5.0F, 18.0F, 2.0F, this.headerColor.getRGB());
         harmony.render(e.getStack(), text, (double)(x + 27.0F), (double)(y + 7.0F), this.headerColor, true, 0.3);
         String duration = StringUtil.formatTickDuration(effectInfo.duration);
         harmony.render(e.getStack(), duration, (double)(x + 27.0F), (double)(y + 17.0F), Color.WHITE, true, 0.25);
         MobEffectTextureManager mobeffecttexturemanager = mc.getMobEffectTextures();
         TextureAtlasSprite textureatlassprite = mobeffecttexturemanager.get(entry.getKey());
         this.list.add(() -> {
            RenderSystem.setShaderTexture(0, textureatlassprite.atlasLocation());
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            e.getGuiGraphics().blit((int)(x + 6.0F), (int)(y + 8.0F), 1, 18, 18, textureatlassprite);
         });
         StencilUtils.dispose();
         startY += 34;
         e.getStack().popPose();
      }
   }

   public String getDisplayName(MobEffect effect, EffectDisplay.MobEffectInfo info) {
      String effectName = effect.getDisplayName().getString();
      String amplifierName;
      if (info.amplifier == 0) {
         amplifierName = "";
      } else if (info.amplifier == 1) {
         amplifierName = " " + I18n.get("enchantment.level.2", new Object[0]);
      } else if (info.amplifier == 2) {
         amplifierName = " " + I18n.get("enchantment.level.3", new Object[0]);
      } else if (info.amplifier == 3) {
         amplifierName = " " + I18n.get("enchantment.level.4", new Object[0]);
      } else {
         amplifierName = " " + info.amplifier;
      }

      return effectName + amplifierName;
   }

   public static class MobEffectInfo {
      public SmoothAnimationTimer xTimer = new SmoothAnimationTimer(-60.0F, 0.2F);
      public SmoothAnimationTimer yTimer = new SmoothAnimationTimer(-1.0F, 0.2F);
      public SmoothAnimationTimer durationTimer = new SmoothAnimationTimer(-1.0F, 0.2F);
      public int maxDuration = -1;
      public int duration = 0;
      public int amplifier = 0;
      public boolean shouldDisappear = false;
      public float width;
   }
}
