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
  private final Map<UUID,Match> matches = new HashMap<>();
  private final Set<UUID> pendingMenu = new HashSet<>();
  private final Map<UUID,Snapshot> snapshots = new HashMap<>();
  private final Set<String> arenaWorlds = new HashSet<>();
  private File kitFile; private YamlConfiguration kits; private int sequence = 0;
  private record Snapshot(ItemStack[] items, ItemStack[] armor, ItemStack offhand, GameMode mode, Location location, double health, int food, float saturation, int level, float exp) {}
  private static class Match { final UUID a,b; final String cls,world; boolean ended=false; Match(UUID a, UUID b, String cls, String world){this.a=a;this.b=b;this.cls=cls;this.world=world;} }
  @Override public void onEnable(){saveDefaultConfig(); for(String c:classes) queues.put(c,new ArrayDeque<>()); kitFile=new File(getDataFolder(),"kits.yml"); kits=YamlConfiguration.loadConfiguration(kitFile); getServer().getPluginManager().registerEvents(this,this); Objects.requireNonNull(getCommand("nlpvp")).setExecutor(this); getLogger().info("NL-UniversePVP enabled. Set the PvP portal destination to /nlpvp menu via a command portal or menu.");}
  @Override public void onDisable(){for(UUID id:new ArrayList<>(snapshots.keySet())){Player p=Bukkit.getPlayer(id); if(p!=null) restore(p);} for(String world:new HashSet<>(arenaWorlds)) cleanup(world);}
  private boolean isClass(String c){return queues.containsKey(c);}
  private boolean admin(CommandSender s){if(!s.hasPermission("nluniversepvp.admin")){s.sendMessage("§cGeen toestemming.");return false;}return true;}
  @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
    if(args.length==0){sender.sendMessage("§e/nlpvp menu | leave | save <class> | sethub | status");return true;}
    String sub=args[0].toLowerCase(Locale.ROOT);
    if(sub.equals("menu")){if(!(sender instanceof Player p))return true;if(matches.containsKey(p.getUniqueId())){p.sendMessage("§cJe zit in een wedstrijd.");return true;}leaveQueue(p);openClass(p);return true;}
    if(sub.equals("leave")){if(!(sender instanceof Player p))return true; if(matches.containsKey(p.getUniqueId())){end(matches.get(p.getUniqueId()),p.getUniqueId());}else {leaveQueue(p);restore(p);hub(p);}return true;}
    if(sub.equals("save")&&args.length==2){if(!admin(sender))return true;if(!(sender instanceof Player p))return true;String c=args[1].toLowerCase(Locale.ROOT);if(!isClass(c)){p.sendMessage("§cOnbekende class.");return true;}kits.set(c+".inventory",Arrays.asList(p.getInventory().getStorageContents()));kits.set(c+".armor",Arrays.asList(p.getInventory().getArmorContents()));kits.set(c+".offhand",p.getInventory().getItemInOffHand());try{kits.save(kitFile);}catch(IOException e){getLogger().severe(e.getMessage());}p.sendMessage("§aClass "+c+" opgeslagen!");return true;}
    if(sub.equals("sethub")){if(!admin(sender))return true;if(sender instanceof Player p){getConfig().set("hub-world",p.getWorld().getName());getConfig().set("hub-location",p.getLocation());saveConfig();p.sendMessage("§aHUB locatie opgeslagen.");}return true;}
    if(sub.equals("status")){sender.sendMessage("§eActieve duels: "+new HashSet<>(matches.values()).size()+" | Werelden: "+arenaWorlds.size());for(String c:classes)sender.sendMessage("§7"+c+": "+queues.get(c).size()+" wachtend");return true;}
    sender.sendMessage("§cOnbekend commando.");return true;
  }
  private void openClass(Player p){Inventory inv=Bukkit.createInventory(null,27,"§8Kies je PvP-class");Material[] icons={Material.NETHERITE_SWORD,Material.BOW,Material.SHIELD,Material.IRON_SWORD,Material.BLAZE_ROD};for(int i=0;i<5;i++){ItemStack item=new ItemStack(icons[i]);ItemMeta meta=item.getItemMeta();meta.setDisplayName("§a"+classes[i].toUpperCase(Locale.ROOT));item.setItemMeta(meta);inv.setItem(11+i,item);}p.openInventory(inv);}
  private void openDeath(Player p){Inventory inv=Bukkit.createInventory(null,27,"§8Na het gevecht");String[] names={"§aTerug naar HUB","§aOpnieuw dezelfde class","§aAndere class kiezen"};Material[] mats={Material.COMPASS,Material.DIAMOND_SWORD,Material.NETHER_STAR};for(int i=0;i<3;i++){ItemStack it=new ItemStack(mats[i]);ItemMeta m=it.getItemMeta();m.setDisplayName(names[i]);it.setItemMeta(m);inv.setItem(11+i*2,it);}p.openInventory(inv);}
  @EventHandler public void click(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String title=e.getView().getTitle();if(title.equals("§8Kies je PvP-class")){e.setCancelled(true);int slot=e.getRawSlot();if(slot>=11&&slot<=15){p.closeInventory();join(p,classes[slot-11]);}}else if(title.equals("§8Na het gevecht")){e.setCancelled(true);int slot=e.getRawSlot();if(slot==11){pendingMenu.remove(p.getUniqueId());p.closeInventory();restore(p);hub(p);}else if(slot==13){pendingMenu.remove(p.getUniqueId());p.closeInventory();String c=selected.get(p.getUniqueId());if(c!=null)join(p,c);else openClass(p);}else if(slot==15){pendingMenu.remove(p.getUniqueId());p.closeInventory();openClass(p);}}}
  @EventHandler public void drag(InventoryDragEvent e){String title=e.getView().getTitle();if(title.equals("§8Kies je PvP-class")||title.equals("§8Na het gevecht"))e.setCancelled(true);}
  private void save(Player p){snapshots.computeIfAbsent(p.getUniqueId(),id->new Snapshot(p.getInventory().getStorageContents().clone(),p.getInventory().getArmorContents().clone(),p.getInventory().getItemInOffHand().clone(),p.getGameMode(),p.getLocation().clone(),p.getHealth(),p.getFoodLevel(),p.getSaturation(),p.getLevel(),p.getExp()));}
  private void kit(Player p,String c){p.getInventory().clear();p.getInventory().setArmorContents(new ItemStack[4]);p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));List<?> items=kits.getList(c+".inventory");if(items!=null){ItemStack[] arr=new ItemStack[36];for(int i=0;i<Math.min(36,items.size());i++)if(items.get(i) instanceof ItemStack it)arr[i]=it.clone();p.getInventory().setStorageContents(arr);}List<?> armor=kits.getList(c+".armor");if(armor!=null){ItemStack[] arr=new ItemStack[4];for(int i=0;i<Math.min(4,armor.size());i++)if(armor.get(i) instanceof ItemStack it)arr[i]=it.clone();p.getInventory().setArmorContents(arr);}ItemStack off=kits.getItemStack(c+".offhand");if(off!=null)p.getInventory().setItemInOffHand(off.clone());p.setGameMode(GameMode.SURVIVAL);p.setHealth(20);p.setFoodLevel(20);p.setSaturation(20);}
  private void restore(Player p){Snapshot s=snapshots.remove(p.getUniqueId());if(s==null)return;p.getInventory().setStorageContents(s.items());p.getInventory().setArmorContents(s.armor());p.getInventory().setItemInOffHand(s.offhand());p.setGameMode(s.mode());p.setHealth(Math.min(s.health(),p.getMaxHealth()));p.setFoodLevel(s.food());p.setSaturation(s.saturation());p.setLevel(s.level());p.setExp(s.exp());}
  private void hub(Player p){Location loc=getConfig().getLocation("hub-location");if(loc==null){World w=Bukkit.getWorld(getConfig().getString("hub-world","hub"));if(w!=null)loc=w.getSpawnLocation();}if(loc!=null)p.teleport(loc);}
  private void leaveQueue(Player p){for(Deque<UUID> q:queues.values())q.remove(p.getUniqueId());}
  private void join(Player p,String c){if(!isClass(c))return;if(!kits.contains(c+".inventory")){p.sendMessage("§cDeze class is nog niet ingesteld door een beheerder.");return;}leaveQueue(p);save(p);selected.put(p.getUniqueId(),c);p.getInventory().clear();hub(p);p.sendMessage("§eJe wacht op een "+c+"-tegenstander. /nlpvp leave om te stoppen.");Deque<UUID> q=queues.get(c);q.addLast(p.getUniqueId());tryPair(c);}
  private void tryPair(String c){Deque<UUID> q=queues.get(c);int limit=getConfig().getInt("max-active-matches",5);if(new HashSet<>(matches.values()).size()>=limit)return;while(q.size()>=2){Player a=Bukkit.getPlayer(q.removeFirst()),b=Bukkit.getPlayer(q.removeFirst());if(a==null||!a.isOnline()){if(b!=null)q.addFirst(b.getUniqueId());continue;}if(b==null||!b.isOnline()){q.addFirst(a.getUniqueId());continue;}start(a,b,c);return;}}
  private void start(Player a,Player b,String c){String name="nlu_duel_"+c+"_"+(++sequence);World w=Bukkit.createWorld(new WorldCreator(name).type(WorldType.FLAT).generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:plains\"}"));if(w==null){a.sendMessage("§cKon arena niet maken.");b.sendMessage("§cKon arena niet maken.");return;}arenaWorlds.add(name);w.setGameRule(GameRule.DO_MOB_SPAWNING,false);w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);w.setTime(6000);build(w,c);Match m=new Match(a.getUniqueId(),b.getUniqueId(),c,name);matches.put(m.a,m);matches.put(m.b,m);kit(a,c);kit(b,c);int y=getConfig().getInt("arena-y",90)+2;a.teleport(new Location(w,-35.5,y,0.5,-90,0));b.teleport(new Location(w,35.5,y,0.5,90,0));a.sendMessage("§aDuel tegen "+b.getName()+"!");b.sendMessage("§aDuel tegen "+a.getName()+"!");}
  private void build(World w,String c){int y=getConfig().getInt("arena-y",90);int size=100;Material floor=switch(c){case "warrior"->Material.STONE_BRICKS;case "archer"->Material.GRASS_BLOCK;case "tank"->Material.SMOOTH_SANDSTONE;case "assassin"->Material.DEEPSLATE_TILES;default->Material.POLISHED_AMETHYST;};Material wall=switch(c){case "warrior"->Material.COBBLESTONE_WALL;case "archer"->Material.OAK_FENCE;case "tank"->Material.SANDSTONE_WALL;case "assassin"->Material.BLACKSTONE_WALL;default->Material.PURPUR_WALL;};for(int x=-50;x<50;x++)for(int z=-50;z<50;z++){w.getBlockAt(x,y,z).setType(floor,false);if(x==-50||x==49||z==-50||z==49)for(int h=1;h<=5;h++)w.getBlockAt(x,y+h,z).setType(wall,false);}for(int x=-30;x<=30;x+=15)for(int z=-30;z<=30;z+=15){if(Math.abs(x)==30&&z==0)continue;if(x==0&&z==0)continue;Material pillar=switch(c){case "warrior"->Material.STONE_BRICKS;case "archer"->Material.OAK_LOG;case "tank"->Material.CHISELED_SANDSTONE;case "assassin"->Material.POLISHED_BLACKSTONE_BRICKS;default->Material.AMETHYST_BLOCK;};for(int h=1;h<=3;h++)w.getBlockAt(x,y+h,z).setType(pillar,false);}w.setSpawnLocation(0,y+2,0);}
  @EventHandler public void death(PlayerDeathEvent e){Player p=e.getEntity();Match m=matches.get(p.getUniqueId());if(m==null)return;e.getDrops().clear();e.setDroppedExp(0);end(m,p.getUniqueId());}
  private void end(Match m,UUID loser){if(m.ended)return;m.ended=true;matches.remove(m.a);matches.remove(m.b);for(UUID id:List.of(m.a,m.b)){Player p=Bukkit.getPlayer(id);if(p!=null){if(id.equals(loser)){pendingMenu.add(id);}else{restore(p);hub(p);pendingMenu.add(id);Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline())openDeath(p);},2L);}p.sendMessage(id.equals(loser)?"§cJe hebt verloren.":"§aJe hebt gewonnen!");}}Bukkit.getScheduler().runTaskLater(this,()->{World w=Bukkit.getWorld(m.world);if(w!=null)for(Player p:new ArrayList<>(w.getPlayers())){restore(p);hub(p);}cleanup(m.world);for(String c:classes)tryPair(c);},40L);}
  @EventHandler public void respawn(PlayerRespawnEvent e){Player p=e.getPlayer();if(!pendingMenu.contains(p.getUniqueId()))return;Location loc=getConfig().getLocation("hub-location");if(loc==null){World w=Bukkit.getWorld(getConfig().getString("hub-world","hub"));if(w!=null)loc=w.getSpawnLocation();}if(loc!=null)e.setRespawnLocation(loc);Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline()){restore(p);openDeath(p);}},2L);}
  @EventHandler public void quit(PlayerQuitEvent e){Player p=e.getPlayer();leaveQueue(p);Match m=matches.get(p.getUniqueId());if(m!=null)end(m,p.getUniqueId());restore(p);pendingMenu.remove(p.getUniqueId());}
  @EventHandler public void teleport(PlayerTeleportEvent e){Match m=matches.get(e.getPlayer().getUniqueId());if(m!=null&&e.getTo()!=null&&!e.getTo().getWorld().getName().equals(m.world)){e.setCancelled(true);e.getPlayer().sendMessage("§cGebruik /nlpvp leave om op te geven.");}}
  private void cleanup(String name){World w=Bukkit.getWorld(name);if(w!=null){for(Player p:new ArrayList<>(w.getPlayers()))hub(p);Bukkit.unloadWorld(w,false);}arenaWorlds.remove(name);File dir=new File(Bukkit.getWorldContainer(),name);delete(dir);}
  private void delete(File f){if(!f.exists())return;File[] files=f.listFiles();if(files!=null)for(File sub:files)delete(sub);if(!f.delete())getLogger().warning("Could not delete "+f);}
}
