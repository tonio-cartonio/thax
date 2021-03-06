package com.matt.forgehax.mods;

import static com.matt.forgehax.Helper.getLocalPlayer;
import static com.matt.forgehax.Helper.getModManager;

import com.matt.forgehax.util.command.Setting;
import com.matt.forgehax.util.mod.Category;
import com.matt.forgehax.util.mod.ToggleMod;
import com.matt.forgehax.util.mod.loader.RegisterMod;
import com.mojang.realmsclient.gui.ChatFormatting;
import joptsimple.internal.Strings;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.Display;

@RegisterMod
public class AutoReply extends ToggleMod {

  public final Setting<String> text =
      getCommandStub()
          .builders()
          .<String>newSettingBuilder()
          .name("text")
          .description("Text to reply with")
          .defaultTo("fuck off newfag")
          .build();

  public final Setting<Boolean> dms =
      getCommandStub()
          .builders()
          .<Boolean>newSettingBuilder()
          .name("dms-mode")
          .description("Makes AutoReply work only in dms")
          .defaultTo(false)
          .build();

  public final Setting<String> search =
      getCommandStub()
          .builders()
          .<String>newSettingBuilder()
          .name("search")
          .description("Text to search for in message")
          .defaultTo("whispers: ")
          .build();

  private final Setting<Boolean> onLostFocus =
      getCommandStub()
          .builders()
          .<Boolean>newSettingBuilder()
          .name("no-focus")
          .description("Makes the mod only work on lost focus")
          .defaultTo(false)
          .build();

  public AutoReply() {
    super(Category.CHAT, "AutoReply", false, "Automatically talk in chat if finds a strings");
  }

  private int replies = 0;

  @Override
  protected void onEnabled() {
    replies = 0;
  }

  @SubscribeEvent
  public void onClientChat(ClientChatReceivedEvent event) {
    String message = event.getMessage().getUnformattedText();
    if (message.contains(search.get()) && !message.startsWith(MC.getSession().getUsername())) {
      String append;
      if (dms.get()) {
        append = "/r ";
      } else {
        append = Strings.EMPTY;
      }

      if (!onLostFocus.getAsBoolean() || !Display.isActive()) {
        getLocalPlayer().sendChatMessage(append + text.get());
        if (getModManager().get(MatrixNotifications.class).get().isEnabled()) {
          getModManager().get(MatrixNotifications.class).get().send_notify(message);
        }
        replies++;
      }
    }
  }

  @Override
  public String getDisplayText() {
    return (super.getDisplayText() + " [" + TextFormatting.GRAY + replies + TextFormatting.RESET + "]");
  }

  @Override
  public String getDebugDisplayText() {
    if (!dms.get()) {
      return super.getDebugDisplayText() + ChatFormatting.GRAY + " [" + ChatFormatting.WHITE + "C" + ChatFormatting.GRAY + "]" + ChatFormatting.WHITE;
    }
    else {
      return super.getDebugDisplayText() + ChatFormatting.GRAY + " [" + ChatFormatting.WHITE + "R" + ChatFormatting.GRAY + "]" + ChatFormatting.WHITE;
    }
  }
}
