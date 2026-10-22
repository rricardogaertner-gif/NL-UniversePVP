package nl.universe.pvp;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class PvPPlugin extends JavaPlugin implements Listener, CommandExecutor {
  private final String[] classes = {"warrior","archer","tank","assassin","mage"};
  private final Map<String,Deque<UUID>> queues = new HashMap<>();
  private final Map<UUID,String> selected = new HashMap<>();
  private final Set<UUID> ffaPlayers = new HashSet<>();
  private final Random random = new Random();
  private String ffaWorldName = "nlu_ffa";
  private final Map<UUID,Match> matches = new HashMap<>();
  private final Set<UUID> pendingMenu = new HashSet<>();
  private final Map<UUID,Snapshot> snapshots = new HashMap<>();
  private final Set<String> arenaWorlds = new HashSet<>();
  private File snapshotDir; private File kitFile; private YamlConfiguration kits; private int sequence = 0;
  private record Snapshot(ItemStack[] items, ItemStack[] armor, ItemStack offhand, GameMode mode, Location location, double health, int food, float saturation, int level, float exp) {}
  private static class Match { final UUID a,b; final String cls,world; boolean ended=false; Match(UUID a, UUID b, String cls, String world){this.a=a;this.b=b;this.cls=cls;this.world=world;} }
  @Override public void onEnable(){saveDefaultConfig(); for(String c:classes) queues.put(c,new ArrayDeque<>()); snapshotDir=new File(getDataFolder(),"recovery"); snapshotDir.mkdirs(); kitFile=new File(getDataFolder(),"kits.yml"); kits=YamlConfiguration.loadConfiguration(kitFile); getServer().getPluginManager().registerEvents(this,this); Objects.requireNonNull(getCommand("nlpvp")).setExecutor(this); ffaWorldName=getConfig().getString("ffa-world","nlu_ffa"); getLogger().info("NL-UniversePVP enabled. Set the PvP portal destination to /nlpvp menu via a command portal or menu.");}
  @Override public void onDisable(){matches.clear();ffaPlayers.clear();for(UUID id:new ArrayList<>(snapshots.keySet())){Player p=Bukkit.getPlayer(id); if(p!=null){restore(p);hub(p);}} for(String world:new HashSet<>(arenaWorlds)) cleanup(world);}
  private boolean isClass(String c){return queues.containsKey(c);}
  private boolean admin(CommandSender s){if(!s.hasPermission("nluniversepvp.admin")){s.sendMessage("§cGeen toestemming.");return false;}return true;}
  @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
    if(args.length==0){sender.sendMessage("§e/nlpvp menu | openmenu | leave | save <class> | sethub | status");return true;}
    String sub=args[0].toLowerCase(Locale.ROOT);
    if(sub.equals("openmenu")){
      if(!(sender instanceof BlockCommandSender blockSender)){
        sender.sendMessage("§cGebruik dit commando vanuit de PvP JOIN-commandblock.");
        return true;
      }
      Location origin=blockSender.getBlock().getLocation().add(0.5,0.5,0.5);
      Player nearest=null;
      double closest=25.0;
      for(Player candidate:origin.getWorld().getPlayers()){
        if(candidate.getGameMode()==GameMode.SPECTATOR)continue;
        double distance=candidate.getLocation().distanceSquared(origin);
        if(distance<=closest){closest=distance;nearest=candidate;}
      }
      if(nearest==null){sender.sendMessage("§eGeen speler binnen 5 blokken.");return true;}if(!nearest.getWorld().getName().equals(getConfig().getString("hub-world","nluniverse_hub"))){sender.sendMessage("§cAlleen vanuit de HUB.");return true;}
      if(matches.containsKey(nearest.getUniqueId())||ffaPlayers.contains(nearest.getUniqueId())){
        nearest.sendMessage("§cJe zit al in een wedstrijd.");return true;
      }
      leaveQueue(nearest);
      openClass(nearest);
      return true;
    }
    if(sub.equals("menu")){if(!(sender instanceof Player p))return true;if(!p.getWorld().getName().equals(getConfig().getString("hub-world","nluniverse_hub"))){p.sendMessage("§cOpen het PvP-menu vanuit de HUB.");return true;}if(matches.containsKey(p.getUniqueId())||ffaPlayers.contains(p.getUniqueId())){p.sendMessage("§cJe zit al in PvP.");return true;}leaveQueue(p);openClass(p);return true;}
    if(sub.equals("leave")){if(!(sender instanceof Player p))return true; if(matches.containsKey(p.getUniqueId())){end(matches.get(p.getUniqueId()),p.getUniqueId());}else if(ffaPlayers.remove(p.getUniqueId())){restore(p);hub(p);}else {leaveQueue(p);if(snapshots.containsKey(p.getUniqueId())||recoveryFile(p.getUniqueId()).exists()){restore(p);hub(p);}else p.sendMessage("§eJe zit niet in PvP.");}return true;}
    if(sub.equals("save")&&args.length==2){if(!admin(sender))return true;if(!(sender instanceof Player p))return true;String c=args[1].toLowerCase(Locale.ROOT);if(!isClass(c)){p.sendMessage("§cOnbekende class.");return true;}kits.set(c+".inventory",Arrays.asList(p.getInventory().getStorageContents()));kits.set(c+".armor",Arrays.asList(p.getInventory().getArmorContents()));kits.set(c+".offhand",p.getInventory().getItemInOffHand());try{kits.save(kitFile);}catch(IOException e){getLogger().severe(e.getMessage());}p.sendMessage("§aClass "+c+" opgeslagen!");return true;}
    if(sub.equals("sethub")){if(!admin(sender))return true;if(sender instanceof Player p){getConfig().set("hub-world",p.getWorld().getName());getConfig().set("hub-location",p.getLocation());saveConfig();p.sendMessage("§aHUB locatie opgeslagen.");}return true;}
    if(sub.equals("status")){sender.sendMessage("§eActieve duels: "+new HashSet<>(matches.values()).size()+" | Werelden: "+arenaWorlds.size());for(String c:classes)sender.sendMessage("§7"+c+": "+queues.get(c).size()+" wachtend");return true;}
    sender.sendMessage("§cOnbekend commando.");return true;
  }
  private void openClass(Player p){Inventory inv=Bukkit.createInventory(null,27,"§8Kies je PvP-class");Material[] icons={Material.NETHERITE_SWORD,Material.BOW,Material.SHIELD,Material.IRON_SWORD,Material.BLAZE_ROD};for(int i=0;i<5;i++){ItemStack item=new ItemStack(icons[i]);ItemMeta meta=item.getItemMeta();meta.setDisplayName("§a"+classes[i].toUpperCase(Locale.ROOT));item.setItemMeta(meta);inv.setItem(11+i,item);}p.openInventory(inv);}
  private void openMode(Player p){Inventory inv=Bukkit.createInventory(null,27,"§8Kies je PvP-modus"); ItemStack duel=new ItemStack(Material.IRON_SWORD),ffa=new ItemStack(Material.NETHERITE_AXE); ItemMeta dm=duel.getItemMeta(),fm=ffa.getItemMeta();dm.setDisplayName("§a1v1 Duel");fm.setDisplayName("§dFree For All");duel.setItemMeta(dm);ffa.setItemMeta(fm);inv.setItem(11,duel);inv.setItem(15,ffa);p.openInventory(inv);}
  private void openDeath(Player p){Inventory inv=Bukkit.createInventory(null,27,"§8Na het gevecht");String[] names={"§aTerug naar HUB","§aOpnieuw dezelfde class","§aAndere class kiezen"};Material[] mats={Material.COMPASS,Material.DIAMOND_SWORD,Material.NETHER_STAR};for(int i=0;i<3;i++){ItemStack it=new ItemStack(mats[i]);ItemMeta m=it.getItemMeta();m.setDisplayName(names[i]);it.setItemMeta(m);inv.setItem(11+i*2,it);}p.openInventory(inv);}
  @EventHandler public void click(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String title=e.getView().getTitle();if(title.equals("§8Kies je PvP-class")){e.setCancelled(true);int slot=e.getRawSlot();if(slot>=11&&slot<=15){p.closeInventory();selected.put(p.getUniqueId(),classes[slot-11]);openMode(p);}}else if(title.equals("§8Kies je PvP-modus")){e.setCancelled(true);int slot=e.getRawSlot();if(slot==11||slot==15){p.closeInventory();String c=selected.get(p.getUniqueId());if(c==null)return;if(slot==11)join(p,c);else joinFfa(p,c);}}else if(title.equals("§8Na het gevecht")){e.setCancelled(true);int slot=e.getRawSlot();if(slot==11){pendingMenu.remove(p.getUniqueId());p.closeInventory();restore(p);hub(p);}else if(slot==13){pendingMenu.remove(p.getUniqueId());p.closeInventory();String c=selected.get(p.getUniqueId());if(c!=null)openMode(p);else openClass(p);}else if(slot==15){pendingMenu.remove(p.getUniqueId());p.closeInventory();openClass(p);}}}
  @EventHandler public void drag(InventoryDragEvent e){String title=e.getView().getTitle();if(title.equals("§8Kies je PvP-class")||title.equals("§8Kies je PvP-modus")||title.equals("§8Na het gevecht"))e.setCancelled(true);}
  private File recoveryFile(UUID id){return new File(snapshotDir,id.toString()+".yml");}
  private void save(Player p){
    UUID id=p.getUniqueId();if(snapshots.containsKey(id))return;if(recoveryFile(id).exists())throw new IllegalStateException("Unrecovered PvP backup exists");
    Snapshot snapshot=new Snapshot(p.getInventory().getStorageContents().clone(),p.getInventory().getArmorContents().clone(),p.getInventory().getItemInOffHand().clone(),p.getGameMode(),p.getLocation().clone(),p.getHealth(),p.getFoodLevel(),p.getSaturation(),p.getLevel(),p.getExp());
    YamlConfiguration data=new YamlConfiguration();data.set("inventory",Arrays.asList(snapshot.items()));data.set("armor",Arrays.asList(snapshot.armor()));data.set("offhand",snapshot.offhand());data.set("gamemode",snapshot.mode().name());data.set("location",snapshot.location());data.set("health",snapshot.health());data.set("food",snapshot.food());data.set("saturation",snapshot.saturation());data.set("level",snapshot.level());data.set("exp",snapshot.exp());
    try{data.save(recoveryFile(id));snapshots.put(id,snapshot);}catch(IOException ex){getLogger().severe("Kon PvP-inventaris niet opslaan voor "+p.getName()+": "+ex.getMessage());throw new IllegalStateException("PvP inventory backup failed",ex);}
  }
  private Snapshot loadSnapshot(UUID id){
    File file=recoveryFile(id);if(!file.isFile())return null;
    YamlConfiguration d=YamlConfiguration.loadConfiguration(file);
    ItemStack[] items=new ItemStack[36],armor=new ItemStack[4];List<?> stored=d.getList("inventory"),equipment=d.getList("armor");
    if(stored!=null)for(int i=0;i<Math.min(36,stored.size());i++)if(stored.get(i) instanceof ItemStack it)items[i]=it;
    if(equipment!=null)for(int i=0;i<Math.min(4,equipment.size());i++)if(equipment.get(i) instanceof ItemStack it)armor[i]=it;
    String gm=d.getString("gamemode","SURVIVAL");GameMode mode;try{mode=GameMode.valueOf(gm);}catch(IllegalArgumentException ex){mode=GameMode.SURVIVAL;}
    Location loc=d.getLocation("location");if(loc==null)loc=Bukkit.getWorlds().get(0).getSpawnLocation();
    return new Snapshot(items,armor,d.getItemStack("offhand"),mode,loc,d.getDouble("health",20),d.getInt("food",20),(float)d.getDouble("saturation",5),d.getInt("level",0),(float)d.getDouble("exp",0));
  }
  private void kit(Player p,String c){p.getInventory().clear();p.getInventory().setArmorContents(new ItemStack[4]);p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));List<?> items=kits.getList(c+".inventory");if(items!=null){ItemStack[] arr=new ItemStack[36];for(int i=0;i<Math.min(36,items.size());i++)if(items.get(i) instanceof ItemStack it)arr[i]=it.clone();p.getInventory().setStorageContents(arr);}List<?> armor=kits.getList(c+".armor");if(armor!=null){ItemStack[] arr=new ItemStack[4];for(int i=0;i<Math.min(4,armor.size());i++)if(armor.get(i) instanceof ItemStack it)arr[i]=it.clone();p.getInventory().setArmorContents(arr);}ItemStack off=kits.getItemStack(c+".offhand");if(off!=null)p.getInventory().setItemInOffHand(off.clone());p.setGameMode(GameMode.SURVIVAL);p.setHealth(20);p.setFoodLevel(20);p.setSaturation(20);}
  private void restore(Player p){
    UUID id=p.getUniqueId();Snapshot s=snapshots.get(id);if(s==null)s=loadSnapshot(id);if(s==null)return;
    p.getInventory().setStorageContents(s.items());p.getInventory().setArmorContents(s.armor());p.getInventory().setItemInOffHand(s.offhand()==null?new ItemStack(Material.AIR):s.offhand());
    p.setGameMode(s.mode());p.setHealth(Math.max(1,Math.min(s.health(),p.getMaxHealth())));p.setFoodLevel(s.food());p.setSaturation(s.saturation());p.setLevel(s.level());p.setExp(s.exp());
    snapshots.remove(id);File f=recoveryFile(id);if(f.exists()&&!f.delete())getLogger().warning("Kon recovery-bestand niet verwijderen: "+f.getName());
  }
  private void hub(Player p){Location loc=getConfig().getLocation("hub-location");if(loc==null){World w=Bukkit.getWorld(getConfig().getString("hub-world","nluniverse_hub"));if(w!=null)loc=w.getSpawnLocation();}if(loc!=null)p.teleport(loc);}
  private void leaveQueue(Player p){for(Deque<UUID> q:queues.values())q.remove(p.getUniqueId());}
  private void join(Player p,String c){if(!isClass(c))return;if(!kits.contains(c+".inventory")){p.sendMessage("§cDeze class is nog niet ingesteld door een beheerder.");return;}leaveQueue(p);try{save(p);}catch(IllegalStateException ex){p.sendMessage("§cKon je inventaris niet veilig opslaan. PvP geannuleerd.");return;}selected.put(p.getUniqueId(),c);p.getInventory().clear();hub(p);p.sendMessage("§eJe wacht op een "+c+"-tegenstander. /nlpvp leave om te stoppen.");Deque<UUID> q=queues.get(c);q.addLast(p.getUniqueId());tryPair(c);}
  private void joinFfa(Player p,String c){
    if(!kits.contains(c+".inventory")){p.sendMessage("§cDeze class is nog niet ingesteld door een beheerder.");return;}
    leaveQueue(p);
    World w=Bukkit.getWorld(ffaWorldName);
    if(w==null){w=Bukkit.createWorld(new WorldCreator(ffaWorldName).type(WorldType.FLAT).generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:plains\"}")));}
    if(w==null){p.sendMessage("§cKon FFA-wereld niet maken.");return;}
    if(!getConfig().getBoolean("ffa-built",false)){
      build(w,"warrior");getConfig().set("ffa-built",true);saveConfig();
    }
    try{save(p);}catch(IllegalStateException ex){p.sendMessage("§cKon je inventaris niet veilig opslaan. PvP geannuleerd.");return;}selected.put(p.getUniqueId(),c);kit(p,c);
    int y=getConfig().getInt("arena-y",90)+2;
    int[][] spots={{-35,-35},{35,35},{-35,35},{35,-35},{0,35},{0,-35}};
    int[] spot=spots[random.nextInt(spots.length)];
    p.teleport(new Location(w,spot[0]+0.5,y,spot[1]+0.5));
    ffaPlayers.add(p.getUniqueId());
    p.sendMessage("§dFFA gestart! §7Gebruik /nlpvp leave om terug te gaan.");
  }
  private void tryPair(String c){Deque<UUID> q=queues.get(c);int limit=getConfig().getInt("max-active-matches",5);if(new HashSet<>(matches.values()).size()>=limit)return;while(q.size()>=2){Player a=Bukkit.getPlayer(q.removeFirst()),b=Bukkit.getPlayer(q.removeFirst());if(a==null||!a.isOnline()){if(b!=null)q.addFirst(b.getUniqueId());continue;}if(b==null||!b.isOnline()){q.addFirst(a.getUniqueId());continue;}start(a,b,c);return;}}
  private void start(Player a,Player b,String c){String name="nlu_duel_"+c+"_"+(++sequence);World w=Bukkit.createWorld(new WorldCreator(name).type(WorldType.FLAT).generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:plains\"}"));if(w==null){a.sendMessage("§cKon arena niet maken.");b.sendMessage("§cKon arena niet maken.");return;}arenaWorlds.add(name);w.setGameRule(GameRule.DO_MOB_SPAWNING,false);w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);w.setTime(6000);build(w,c);Match m=new Match(a.getUniqueId(),b.getUniqueId(),c,name);matches.put(m.a,m);matches.put(m.b,m);kit(a,c);kit(b,c);int y=getConfig().getInt("arena-y",90)+2;a.teleport(new Location(w,-35.5,y,0.5,-90,0));b.teleport(new Location(w,35.5,y,0.5,90,0));a.sendMessage("§aDuel tegen "+b.getName()+"!");b.sendMessage("§aDuel tegen "+a.getName()+"!");}
  private void build(World w,String c){int y=getConfig().getInt("arena-y",90);int size=100;Material floor=switch(c){case "warrior"->Material.STONE_BRICKS;case "archer"->Material.GRASS_BLOCK;case "tank"->Material.SMOOTH_SANDSTONE;case "assassin"->Material.DEEPSLATE_TILES;default->Material.AMETHYST_BLOCK;};Material wall=switch(c){case "warrior"->Material.COBBLESTONE_WALL;case "archer"->Material.OAK_FENCE;case "tank"->Material.SANDSTONE_WALL;case "assassin"->Material.BLACKSTONE_WALL;default->Material.PURPUR_BLOCK;};for(int x=-50;x<50;x++)for(int z=-50;z<50;z++){w.getBlockAt(x,y,z).setType(floor,false);if(x==-50||x==49||z==-50||z==49)for(int h=1;h<=5;h++)w.getBlockAt(x,y+h,z).setType(wall,false);}for(int x=-30;x<=30;x+=15)for(int z=-30;z<=30;z+=15){if(Math.abs(x)==30&&z==0)continue;if(x==0&&z==0)continue;Material pillar=switch(c){case "warrior"->Material.STONE_BRICKS;case "archer"->Material.OAK_LOG;case "tank"->Material.CHISELED_SANDSTONE;case "assassin"->Material.POLISHED_BLACKSTONE_BRICKS;default->Material.AMETHYST_BLOCK;};for(int h=1;h<=3;h++)w.getBlockAt(x,y+h,z).setType(pillar,false);}w.setSpawnLocation(0,y+2,0);}
  @EventHandler public void death(PlayerDeathEvent e){Player p=e.getEntity();Match m=matches.get(p.getUniqueId());if(m==null&&ffaPlayers.remove(p.getUniqueId())){e.getDrops().clear();e.setDroppedExp(0);pendingMenu.add(p.getUniqueId());p.sendMessage("§cJe bent uitgeschakeld in FFA.");return;}if(m==null)return;e.getDrops().clear();e.setDroppedExp(0);end(m,p.getUniqueId());}
  private void end(Match m,UUID loser){if(m.ended)return;m.ended=true;matches.remove(m.a);matches.remove(m.b);for(UUID id:List.of(m.a,m.b)){Player p=Bukkit.getPlayer(id);if(p!=null){if(id.equals(loser)){pendingMenu.add(id);}else{restore(p);hub(p);pendingMenu.add(id);Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline())openDeath(p);},2L);}p.sendMessage(id.equals(loser)?"§cJe hebt verloren.":"§aJe hebt gewonnen!");}}Bukkit.getScheduler().runTaskLater(this,()->{World w=Bukkit.getWorld(m.world);if(w!=null)for(Player p:new ArrayList<>(w.getPlayers())){restore(p);hub(p);}cleanup(m.world);for(String c:classes)tryPair(c);},40L);}
  @EventHandler public void respawn(PlayerRespawnEvent e){Player p=e.getPlayer();if(!pendingMenu.contains(p.getUniqueId()))return;Location loc=getConfig().getLocation("hub-location");if(loc==null){World w=Bukkit.getWorld(getConfig().getString("hub-world","nluniverse_hub"));if(w!=null)loc=w.getSpawnLocation();}if(loc!=null)e.setRespawnLocation(loc);Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline()){restore(p);openDeath(p);}},2L);}
  @EventHandler public void quit(PlayerQuitEvent e){Player p=e.getPlayer();leaveQueue(p);Match m=matches.get(p.getUniqueId());if(m!=null)end(m,p.getUniqueId());ffaPlayers.remove(p.getUniqueId());restore(p);pendingMenu.remove(p.getUniqueId());}
  @EventHandler public void onPlayerJoin(PlayerJoinEvent e){
    Player p=e.getPlayer();UUID id=p.getUniqueId();if(recoveryFile(id).isFile()){
      Bukkit.getScheduler().runTaskLater(this,()->{if(!p.isOnline())return;restore(p);hub(p);p.sendMessage("§aJe inventaris is hersteld na een PvP-onderbreking.");},2L);
    }
  }
  @EventHandler public void teleport(PlayerTeleportEvent e){
    Player p=e.getPlayer();Location to=e.getTo();if(to==null)return;
    UUID id=p.getUniqueId();Match m=matches.get(id);
    boolean leaving=(m!=null&&!to.getWorld().getName().equals(m.world))||(ffaPlayers.contains(id)&&!to.getWorld().getName().equals(ffaWorldName));
    if(leaving){e.setCancelled(true);p.sendMessage("§cGebruik /nlpvp leave om PvP te verlaten.");}
  }
  private void cleanup(String name){World w=Bukkit.getWorld(name);if(w!=null){for(Player p:new ArrayList<>(w.getPlayers()))hub(p);Bukkit.unloadWorld(w,false);}arenaWorlds.remove(name);File dir=new File(Bukkit.getWorldContainer(),name);delete(dir);}
  private void delete(File f){if(!f.exists())return;File[] files=f.listFiles();if(files!=null)for(File sub:files)delete(sub);if(!f.delete())getLogger().warning("Could not delete "+f);}
}
