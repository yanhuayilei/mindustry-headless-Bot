import arc.ApplicationListener;
import arc.Core;
import arc.Events;
import arc.backend.headless.HeadlessApplication;
import arc.struct.ObjectSet;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.core.Logic;
import mindustry.core.NetServer;
import mindustry.core.Platform;
import mindustry.ctype.Content;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.mod.Mods;
import mindustry.net.CrashSender;
import mindustry.net.Net;
import mindustry.server.ServerControl;
import mindustry.server.ServerLauncher;

import java.time.LocalDateTime;
import java.util.Iterator;

public class ServerL implements ApplicationListener {
    static String[] args;

    public ServerL() {
    }

    public static void main(String[] args) {
        try {
            ServerL.args = args;
            Vars.platform = new Platform() {
            };
            Vars.net = new Net(Vars.platform.getNet());
            new HeadlessApplication(new ServerL(), (throwable) -> {
                CrashSender.send(throwable, (f) -> {
                });
            });
        } catch (Throwable var2) {
            CrashSender.send(var2, (f) -> {
            });
        }

    }

    public void init() {
        Core.settings.setDataDirectory(Core.files.local("config"));
        Vars.loadLocales = false;
        Vars.headless = true;
        Vars.loadSettings();
        Vars.init();
        Vars.content.createBaseContent();
        Vars.mods.loadScripts();
        Vars.content.createModContent();
        Vars.content.init();
        if (Vars.mods.hasContentErrors()) {
            Log.err("Error occurred loading mod content:", new Object[0]);
            Iterator var1 = Vars.mods.list().iterator();

            label26:
            while(true) {
                Mods.LoadedMod mod;
                do {
                    if (!var1.hasNext()) {
                        Log.err("The server will now exit.", new Object[0]);
                        System.exit(1);
                        break label26;
                    }

                    mod = (Mods.LoadedMod)var1.next();
                } while(!mod.hasContentErrors());

                Log.err("| &ly[@]", new Object[]{mod.name});
                ObjectSet.ObjectSetIterator var3 = mod.erroredContent.iterator();

                while(var3.hasNext()) {
                    Content cont = (Content)var3.next();
                    Log.err("| | &y@: &c@", new Object[]{cont.minfo.sourceFile.name(), Strings.getSimpleMessage(cont.minfo.baseError).replace("\n", " ")});
                }
            }
        }

        Vars.bases.load();
        Core.app.addListener(new ApplicationListener() {
            public void update() {
                Vars.asyncCore.begin();
            }
        });
        Core.app.addListener(Vars.logic = new Logic());
        Core.app.addListener(Vars.netServer = new NetServer());
        Core.app.addListener(new ServerC(args));
        Core.app.addListener(new ApplicationListener() {
            public void update() {
                Vars.asyncCore.end();
            }
        });
        Vars.mods.eachClass(Mod::init);
        Events.fire(new EventType.ServerLoadEvent());
    }
}
