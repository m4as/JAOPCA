package thelm.jaopca.modules;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import exnihiloadscensio.ExNihiloAdscensio;
import exnihiloadscensio.blocks.BlockSieve.MeshType;
import exnihiloadscensio.config.Config;
import exnihiloadscensio.items.ore.Ore;
import exnihiloadscensio.json.CustomBlockInfoJson;
import exnihiloadscensio.json.CustomItemInfoJson;
import exnihiloadscensio.json.CustomOreJson;
import exnihiloadscensio.registries.SieveRegistry;
import exnihiloadscensio.util.BlockInfo;
import exnihiloadscensio.util.ItemInfo;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.init.Blocks;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import thelm.jaopca.api.EnumEntryType;
import thelm.jaopca.api.IOreEntry;
import thelm.jaopca.api.ItemEntry;
import thelm.jaopca.api.JAOPCAApi;
import thelm.jaopca.api.ModuleBase;
import thelm.jaopca.api.utils.Utils;

public class ModuleExNihiloAdscensio extends ModuleBase {

	public static final ItemEntry PIECE_ENTRY = new ItemEntry(EnumEntryType.ITEM, "piece", new ModelResourceLocation("jaopca:ore_crushed#inventory"));
	public static final ItemEntry CHUNK_ENTRY = new ItemEntry(EnumEntryType.ITEM, "hunk", new ModelResourceLocation("jaopca:ore_broken#inventory"));

	public static final ArrayList<String> EXISTING_ORES = Lists.<String>newArrayList();

	public static final HashMap<IOreEntry, double[]> RARITY_MUTIPLIERS = Maps.<IOreEntry, double[]>newHashMap();

	public static final String ENDER_IO_MESSAGE = "" +
			"<recipeGroup name=\"JAOPCA_ENA\">" +
			"<recipe name=\"%s\" energyCost=\"400\">" +
			"<input>" +
			"<itemStack oreDictionary=\"%s\" />" +
			"</input>" +
			"<output>" +
			"<itemStack oreDictionary=\"%s\" number=\"2\" />" +
			"</output>" +
			"</recipe>" +
			"</recipeGroup>";

	@Override
	public String getName() {
		return "exnihiloadscensio";
	}

	@Override
	public List<ItemEntry> getItemRequests() {
		ArrayList<ItemEntry> ret = Lists.newArrayList(PIECE_ENTRY, CHUNK_ENTRY);
		for(ItemEntry entry : ret) {
			entry.blacklist.addAll(EXISTING_ORES);
		}

		return ret;
	}

	@Override
	public void registerConfigs(Configuration config) {
		for(IOreEntry entry : JAOPCAApi.ENTRY_NAME_TO_ORES_MAP.get("piece")) {
			double[] data = {
					config.get(Utils.to_under_score(entry.getOreName()), "eNAFlintMultiplier", 0.2D).setRequiresMcRestart(true).getDouble(),
					config.get(Utils.to_under_score(entry.getOreName()), "eNAIronMultiplier", 0.2D).setRequiresMcRestart(true).getDouble(),
					config.get(Utils.to_under_score(entry.getOreName()), "eNADiamondMultiplier", 0.1D).setRequiresMcRestart(true).getDouble(),
			};
			RARITY_MUTIPLIERS.put(entry, data);
		}
	}

	@Override
	public void init() {
		for(IOreEntry entry : JAOPCAApi.ENTRY_NAME_TO_ORES_MAP.get("piece")) {
			double[] data = RARITY_MUTIPLIERS.get(entry);
			SieveRegistry.register(Blocks.GRAVEL.getDefaultState(), Utils.getOreStack("piece", entry, 1), Utils.rarityReciprocalF(entry, data[0]), MeshType.FLINT.getID());
			SieveRegistry.register(Blocks.GRAVEL.getDefaultState(), Utils.getOreStack("piece", entry, 1), Utils.rarityReciprocalF(entry, data[1]), MeshType.IRON.getID());
			SieveRegistry.register(Blocks.GRAVEL.getDefaultState(), Utils.getOreStack("piece", entry, 1), Utils.rarityReciprocalF(entry, data[2]), MeshType.DIAMOND.getID());
		}

		for(IOreEntry entry : JAOPCAApi.ENTRY_NAME_TO_ORES_MAP.get("hunk")) {
			GameRegistry.addRecipe(new ShapelessOreRecipe(Utils.getOreStack("hunk", entry, 1), new Object[] {
					"piece"+entry.getOreName(),
					"piece"+entry.getOreName(),
					"piece"+entry.getOreName(),
					"piece"+entry.getOreName(),
			}));
			Utils.addSmelting(Utils.getOreStack("hunk", entry, 1), Utils.getOreStack("ingot", entry, 1), 0.7F);

			if(Config.doTICCompat && Loader.isModLoaded("tconstruct") && FluidRegistry.isFluidRegistered(Utils.to_under_score(entry.getOreName()))) {
				ModuleTinkersConstruct.addMeltingRecipe("hunk"+entry.getOreName(), FluidRegistry.getFluid(Utils.to_under_score(entry.getOreName())), 288);
			}

			if(Config.doEnderIOCompat && Loader.isModLoaded("EnderIO")) {
				addOreSAGMillRecipe("hunk"+entry.getOreName(), "dust"+entry.getOreName());
			}
		}
		ExNihiloAdscensio.configsLoaded = false;
	}

	@Override
	public List<Pair<String, String>> remaps() {
		return Lists.<Pair<String, String>>newArrayList(
				Pair.of("orepiece", "piece"),
				Pair.of("orechunk", "hunk")
				);
	}

	public static void addOreSAGMillRecipe(String input, String output) {
		FMLInterModComms.sendMessage("enderio", "recipe:sagmill", String.format(ENDER_IO_MESSAGE, input, input, output));
	}

	static {
		//yep, only way i could think of adding the blacklist
		//should work
		Gson gson = new GsonBuilder().setPrettyPrinting()
				.registerTypeAdapter(ItemInfo.class, new CustomItemInfoJson())
				.registerTypeAdapter(BlockInfo.class, new CustomBlockInfoJson())
				.registerTypeAdapter(Ore.class, new CustomOreJson()).create();
		ParameterizedType TYPE = new ParameterizedType() {
			@Override
			public Type[] getActualTypeArguments() {
				return new Type[] {Ore.class};
			}

			@Override
			public Type getRawType() {
				return List.class;
			}

			@Override
			public Type getOwnerType() {
				return null;
			}
		};
		ArrayList<String> defaults = Lists.<String>newArrayList(
				"Iron", "Gold", "Copper", "Tin", "Aluminium", "Lead", "Silver", "Nickel", "Ardite", "Cobalt"
				);
		try {
			File file = new File(ExNihiloAdscensio.configDirectory, "OreRegistry.json");
			if(file.exists()) {
				FileReader e = new FileReader(file);
				for(Ore ore : gson.<List<Ore>>fromJson(e, TYPE)) {
					EXISTING_ORES.add(StringUtils.capitalize(ore.getName()));
				};
			}
			else {
				EXISTING_ORES.addAll(defaults);
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			EXISTING_ORES.addAll(defaults);
		}
	}
}
