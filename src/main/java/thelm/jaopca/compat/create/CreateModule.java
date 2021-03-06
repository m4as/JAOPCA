package thelm.jaopca.compat.create;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import thelm.jaopca.api.JAOPCAApi;
import thelm.jaopca.api.config.IDynamicSpecConfig;
import thelm.jaopca.api.forms.IForm;
import thelm.jaopca.api.forms.IFormRequest;
import thelm.jaopca.api.helpers.IMiscHelper;
import thelm.jaopca.api.items.IItemFormType;
import thelm.jaopca.api.items.IItemInfo;
import thelm.jaopca.api.materials.IMaterial;
import thelm.jaopca.api.materials.MaterialType;
import thelm.jaopca.api.modules.IModule;
import thelm.jaopca.api.modules.IModuleData;
import thelm.jaopca.api.modules.JAOPCAModule;
import thelm.jaopca.items.ItemFormType;
import thelm.jaopca.utils.ApiImpl;
import thelm.jaopca.utils.MiscHelper;

@JAOPCAModule(modDependencies = "create")
public class CreateModule implements IModule {

	private static final Set<String> BLACKLIST = new TreeSet<>(Arrays.asList(
			"copper", "gold", "iron", "zinc"));

	private Map<IMaterial, IDynamicSpecConfig> configs;

	private final IForm crushedOreForm = ApiImpl.INSTANCE.newForm(this, "create_crushed_ores", ItemFormType.INSTANCE).
			setMaterialTypes(MaterialType.INGOT).setSecondaryName("create:crushed_ores").setDefaultMaterialBlacklist(BLACKLIST);

	@Override
	public String getName() {
		return "create";
	}

	@Override
	public Multimap<Integer, String> getModuleDependencies() {
		ImmutableSetMultimap.Builder builder = ImmutableSetMultimap.builder();
		builder.put(0, "nuggets");
		return builder.build();
	}

	@Override
	public List<IFormRequest> getFormRequests() {
		return Collections.singletonList(crushedOreForm.toRequest());
	}

	@Override
	public Set<MaterialType> getMaterialTypes() {
		return EnumSet.of(MaterialType.INGOT);
	}

	@Override
	public Set<String> getDefaultMaterialBlacklist() {
		return BLACKLIST;
	}

	@Override
	public void defineMaterialConfig(IModuleData moduleData, Map<IMaterial, IDynamicSpecConfig> configs) {
		this.configs = configs;
	}

	@Override
	public void onCommonSetup(IModuleData moduleData, FMLCommonSetupEvent event) {
		JAOPCAApi api = ApiImpl.INSTANCE;
		CreateHelper helper = CreateHelper.INSTANCE;
		IMiscHelper miscHelper = MiscHelper.INSTANCE;
		IItemFormType itemFormType = ItemFormType.INSTANCE;	
		for(IMaterial material : crushedOreForm.getMaterials()) {
			ResourceLocation oreLocation = miscHelper.getTagLocation("ores", material.getName());
			IItemInfo crushedOreInfo = itemFormType.getMaterialFormInfo(crushedOreForm, material);

			IDynamicSpecConfig config = configs.get(material);
			String configByproduct = config.getDefinedString("create.byproduct", "minecraft:cobblestone",
					s->ForgeRegistries.ITEMS.containsKey(new ResourceLocation(s)), "The byproduct material to output in Create's crushing.");
			Item byproduct = ForgeRegistries.ITEMS.getValue(new ResourceLocation(configByproduct));

			helper.registerMillingRecipe(
					new ResourceLocation("jaopca", "create.ore_to_crushed_milling."+material.getName()),
					oreLocation, 350, new Object[] {
							crushedOreInfo, 1,
					});
			helper.registerCrushingRecipe(
					new ResourceLocation("jaopca", "create.ore_to_crushed_crushing."+material.getName()),
					oreLocation, 350, new Object[] {
							crushedOreInfo, 1,
							crushedOreInfo, 2, 0.3F,
							byproduct, 1, 0.125F,
					});
		}
		for(IMaterial material : moduleData.getMaterials()) {
			ResourceLocation crushedOreLocation = miscHelper.getTagLocation("create:crushed_ores", material.getName());
			ResourceLocation nuggetLocation = miscHelper.getTagLocation("nuggets", material.getName());
			ResourceLocation materialLocation = miscHelper.getTagLocation(material.getType().getFormName(), material.getName());

			api.registerSmeltingRecipe(
					new ResourceLocation("jaopca", "create.crushed_to_ingot_smelting."+material.getName()),
					crushedOreLocation, materialLocation, 1, 0.1F, 200);
			api.registerBlastingRecipe(
					new ResourceLocation("jaopca", "create.crushed_to_ingot_blasting."+material.getName()),
					crushedOreLocation, materialLocation, 1, 0.1F, 50);
			helper.registerSplashingRecipe(
					new ResourceLocation("jaopca", "create.crushed_to_nugget."+material.getName()),
					crushedOreLocation, new Object[] {
							nuggetLocation, 10,
							nuggetLocation, 5, 0.5F,
					});
		}
	}
}
