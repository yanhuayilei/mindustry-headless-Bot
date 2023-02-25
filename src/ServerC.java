import arc.ApplicationListener;
import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.*;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.core.Version;
import mindustry.game.EventType;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.io.JsonIO;
import mindustry.io.SaveIO;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import mindustry.mod.Mods;
import mindustry.net.Administration;
import mindustry.net.Packets;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Scanner;

public class ServerC implements ApplicationListener {
    private static final int roundExtraTime = 12;
    private static final int maxLogLength = 5242880;
    protected static String[] tags = new String[]{"&lc&fb[D]&fr", "&lb&fb[I]&fr", "&ly&fb[W]&fr", "&lr&fb[E]", ""};
    protected static DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");
    protected static DateTimeFormatter autosaveDate = DateTimeFormatter.ofPattern("MM-dd-yyyy_HH-mm-ss");
    public final CommandHandler handler = new CommandHandler("");
    public final Fi logFolder;
    public Runnable serverInput;
    private Fi currentLogFile;
    private boolean inGameOverWait;
    private Timer.Task lastTask;
    private Gamemode lastMode;
    @Nullable
    private Map nextMapOverride;
    private Interval autosaveCount;
    private Thread socketThread;
    private ServerSocket serverSocket;
    private PrintWriter socketOutput;
    private String suggested;
    private boolean autoPaused;

    public ServerC(String[] args) {
        this.logFolder = Core.settings.getDataDirectory().child("logs/");
        this.serverInput = () -> {
            Scanner scan = new Scanner(System.in);

            while(scan.hasNext()) {
                String line = scan.nextLine();
                Core.app.post(() -> {
                    this.handleCommandString(line);
                });
            }

        };
        this.autosaveCount = new Interval();
        this.autoPaused = false;
        this.setup(args);
    }
    protected void registerCommands() {
        this.handler.register("help", "[command]", "Display the command list, or get help for a specific command.", (arg) -> {
            if (arg.length > 0) {
                CommandHandler.Command commandx = (CommandHandler.Command) this.handler.getCommandList().find((c) -> {
                    return c.text.equalsIgnoreCase(arg[0]);
                });
                if (commandx == null) {
                    Log.err("Command " + arg[0] + " not found!", new Object[0]);
                } else {
                    Log.info(commandx.text + ":");
                    Log.info("  &b&lb " + commandx.text + (commandx.paramText.isEmpty() ? "" : " &lc&fi") + commandx.paramText + "&fr - &lw" + commandx.description);
                }
            } else {
                Log.info("Commands:");
                Iterator var4 = this.handler.getCommandList().iterator();

                while (var4.hasNext()) {
                    CommandHandler.Command command = (CommandHandler.Command) var4.next();
                    Log.info("  &b&lb " + command.text + (command.paramText.isEmpty() ? "" : " &lc&fi") + command.paramText + "&fr - &lw" + command.description);
                }
            }

        });
    }
    protected void setup(String[] args) {
        Core.settings.defaults(new Object[]{"bans", "", "admins", "", "shufflemode", "custom", "globalrules", "{reactorExplosions: false, logicUnitBuild: false}"});

        Log.logger = (level1, text) -> {
            if (level1 == Log.LogLevel.err) {
                text = text.replace(ColorCodes.reset, ColorCodes.lightRed + ColorCodes.bold);
            }

            String result = ColorCodes.bold + ColorCodes.lightBlack + "[" + dateTime.format(LocalDateTime.now()) + "] " + ColorCodes.reset + Log.format(tags[level1.ordinal()] + " " + text + "&fr", new Object[0]);
            System.out.println(result);
            /*if (Administration.Config.logging.bool()) {
                this.logToFile("[" + dateTime.format(LocalDateTime.now()) + "] " + Log.formatColors(tags[level1.ordinal()] + " " + text + "&fr", false, new Object[0]));
            }*/

        };
        Log.formatter = (text, useColors, arg) -> {
            text = Strings.format(text.replace("@", "&fb&lb@&fr"), arg);
            return useColors ? Log.addColors(text) : Log.removeColors(text);
        };
        this.registerCommands();
        Core.app.post(() -> {

            Seq<String> commands = new Seq();
            if (args.length > 0) {
                commands.addAll(Strings.join(" ", args).split(","));
                Log.info("Found @ command-line arguments to parse.", new Object[]{commands.size});
            }

            if (!Administration.Config.startCommands.string().isEmpty()) {
                String[] startup = Strings.join(" ", new String[]{Administration.Config.startCommands.string()}).split(",");
                Log.info("Found @ startup commands.", new Object[]{startup.length});
                commands.addAll(startup);
            }

            Iterator var8 = commands.iterator();

            while(var8.hasNext()) {
                String s = (String)var8.next();
                CommandHandler.CommandResponse response = this.handler.handleMessage(s);
                if (response.type != CommandHandler.ResponseType.valid) {
                    Log.err("Invalid command argument sent: '@': @", new Object[]{s, response.type.name()});
                    Log.err("Argument usage: &lb<command-1> <command1-args...>,<command-2> <command-2-args2...>", new Object[0]);
                }
            }

        });
        Vars.customMapDirectory.mkdirs();
        if (Version.build == -1) {
            Log.warn("&lyYour server is running a custom build, which means that client checking is disabled.", new Object[0]);
            Log.warn("&lyIt is highly advised to specify which version you're using by building with gradle args &lb&fb-Pbuildversion=&lr<build>", new Object[0]);
        }


       /* Events.run(EventType.Trigger.update, () -> {
            if (Vars.state.isPlaying() && Administration.Config.autosave.bool() && this.autosaveCount.get((float)(Administration.Config.autosaveSpacing.num() * 60))) {
                int max = Administration.Config.autosaveAmount.num();
                String mapName = (Vars.state.map.file == null ? "unknown" : Vars.state.map.file.nameWithoutExtension()).replace(" ", "_");
                String date = autosaveDate.format(LocalDateTime.now());
                Seq<Fi> autosaves = Vars.saveDirectory.findAll((f) -> {
                    return f.name().startsWith("auto_");
                });
                autosaves.sort((f) -> {
                    return (float)(-f.lastModified());
                });
                if (autosaves.size >= max) {
                    for(int i = max - 1; i < autosaves.size; ++i) {
                        ((Fi)autosaves.get(i)).delete();
                    }
                }

                String fileName = "auto_" + mapName + "_" + date + "." + "msav";
                Fi file = Vars.saveDirectory.child(fileName);
                Log.info("Autosaving...");

                try {
                    SaveIO.save(file);
                    Log.info("Autosave completed.");
                } catch (Throwable var8) {
                    Log.err("Autosave failed.", var8);
                }
            }

        });*/
        Events.on(EventType.PlayEvent.class, (e) -> {
            try {
                JsonValue value = (JsonValue) JsonIO.json.fromJson((Class)null, Core.settings.getString("globalrules"));
                JsonIO.json.readFields(Vars.state.rules, value);
            } catch (Throwable var2) {
                Log.err("Error applying custom rules, proceeding without them.", var2);
            }

        });
        float saveInterval = 60.0F;
        Timer.schedule(() -> {
            Vars.netServer.admins.forceSave();
            Core.settings.forceSave();
        }, saveInterval, saveInterval);
        if (!Vars.mods.orderedMods().isEmpty()) {
            Log.info("@ mods loaded.", new Object[]{Vars.mods.orderedMods().size});
        }

        int unsupported = Vars.mods.list().count((l) -> {
            return !l.enabled();
        });
        if (unsupported > 0) {
            Log.err("There were errors loading @ mod(s):", new Object[]{unsupported});
            Iterator var4 = Vars.mods.list().select((l) -> {
                return !l.enabled();
            }).iterator();

            while(var4.hasNext()) {
                Mods.LoadedMod mod = (Mods.LoadedMod)var4.next();
                Log.err("- @ &ly(" + mod.state + ")", new Object[]{mod.meta.name});
            }
        }

        Events.on(EventType.ServerLoadEvent.class, (e) -> {
            Thread thread = new Thread(this.serverInput, "Server Controls");
            thread.setDaemon(true);
            thread.start();
            Log.info("Server loaded. Type @ for help.", new Object[]{"'help'"});
        });

    }


    public void handleCommandString(String line) {
        CommandHandler.CommandResponse response = this.handler.handleMessage(line);
        if (response.type == CommandHandler.ResponseType.unknownCommand) {
            int minDst = 0;
            CommandHandler.Command closest = null;
            Iterator var5 = this.handler.getCommandList().iterator();

            while(true) {
                CommandHandler.Command command;
                int dst;
                do {
                    do {
                        if (!var5.hasNext()) {
                            if (closest != null && !closest.text.equals("yes")) {
                                Log.err("Command not found. Did you mean \"" + closest.text + "\"?", new Object[0]);
                                this.suggested = line.replace(response.runCommand, closest.text);
                            } else {
                                Log.err("Invalid command. Type 'help' for help.", new Object[0]);
                            }

                            return;
                        }

                        command = (CommandHandler.Command)var5.next();
                        dst = Strings.levenshtein(command.text, response.runCommand);
                    } while(dst >= 3);
                } while(closest != null && dst >= minDst);

                minDst = dst;
                closest = command;
            }
        } else if (response.type == CommandHandler.ResponseType.fewArguments) {
            Log.err("Too few command arguments. Usage: " + response.command.text + " " + response.command.paramText, new Object[0]);
        } else if (response.type == CommandHandler.ResponseType.manyArguments) {
            Log.err("Too many command arguments. Usage: " + response.command.text + " " + response.command.paramText, new Object[0]);
        } else if (response.type == CommandHandler.ResponseType.valid) {
            this.suggested = null;
        }

    }
}
