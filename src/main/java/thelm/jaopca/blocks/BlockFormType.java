package thelm.jaopca.blocks;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.collect.TreeBasedTable;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.registries.IForgeRegistry;
import thelm.jaopca.JAOPCA;
import thelm.jaopca.api.blocks.IBlockFormSettings;
import thelm.jaopca.api.blocks.IBlockFormType;
import thelm.jaopca.api.blocks.IBlockInfo;
import thelm.jaopca.api.blocks.IMaterialFormBlock;
import thelm.jaopca.api.blocks.IMaterialFormBlockItem;
import thelm.jaopca.api.forms.IForm;
import thelm.jaopca.api.materials.IMaterial;
import thelm.jaopca.custom.json.BlockFormSettingsDeserializer;
import thelm.jaopca.custom.json.EnumDeserializer;
import thelm.jaopca.custom.json.MaterialMappedFunctionDeserializer;
import thelm.jaopca.custom.json.VoxelShapeDeserializer;
import thelm.jaopca.custom.utils.BlockDeserializationHelper;
import thelm.jaopca.data.DataInjector;
import thelm.jaopca.forms.FormTypeHandler;
import thelm.jaopca.utils.ApiImpl;

public class BlockFormType implements IBlockFormType {

	private BlockFormType() {};

	public static final BlockFormType INSTANCE = new BlockFormType();
	private static final TreeSet<IForm> FORMS = new TreeSet<>();
	private static final TreeBasedTable<IForm, IMaterial, IMaterialFormBlock> BLOCKS = TreeBasedTable.create();
	private static final TreeBasedTable<IForm, IMaterial, IMaterialFormBlockItem> BLOCK_ITEMS = TreeBasedTable.create();
	private static final TreeBasedTable<IForm, IMaterial, IBlockInfo> BLOCK_INFOS = TreeBasedTable.create();

	public static final Type MATERIAL_FUNCTION_TYPE = new TypeToken<Function<IMaterial, Material>>(){}.getType();
	public static final Type SOUND_TYPE_FUNCTION_TYPE = new TypeToken<Function<IMaterial, SoundType>>(){}.getType();
	public static final Type TOOL_TYPE_FUNCTION_TYPE = new TypeToken<Function<IMaterial, ToolType>>(){}.getType();

	public static void init() {
		FormTypeHandler.registerFormType(INSTANCE);
	}

	@Override
	public String getName() {
		return "block";
	}

	@Override
	public void addForm(IForm form) {
		FORMS.add(form);
	}

	@Override
	public Set<IForm> getForms() {
		return Collections.unmodifiableNavigableSet(FORMS);
	}

	@Override
	public boolean shouldRegister(IForm form, IMaterial material) {
		if(material.getType().isDummy()) {
			return true;
		}
		ResourceLocation tagLocation = new ResourceLocation("forge", form.getSecondaryName()+'/'+material.getName());
		return !ApiImpl.INSTANCE.getItemTags().contains(tagLocation);
	}

	@Override
	public IBlockInfo getMaterialFormInfo(IForm form, IMaterial material) {
		IBlockInfo info = BLOCK_INFOS.get(form, material);
		if(info == null && FORMS.contains(form) && form.getMaterials().contains(material)) {
			info = new BlockInfo(BLOCKS.get(form, material), BLOCK_ITEMS.get(form, material));
			BLOCK_INFOS.put(form, material, info);
		}
		return info;
	}

	@Override
	public IBlockFormSettings getNewSettings() {
		return new BlockFormSettings();
	}

	@Override
	public GsonBuilder configureGsonBuilder(GsonBuilder builder) {
		return builder.
				registerTypeAdapter(MATERIAL_FUNCTION_TYPE,
						new MaterialMappedFunctionDeserializer<>(BlockDeserializationHelper.INSTANCE::getBlockMaterial,
								BlockDeserializationHelper.INSTANCE::getBlockMaterialName)).
				registerTypeAdapter(SOUND_TYPE_FUNCTION_TYPE,
						new MaterialMappedFunctionDeserializer<>(BlockDeserializationHelper.INSTANCE::getSoundType,
								BlockDeserializationHelper.INSTANCE::getSoundTypeName)).
				registerTypeAdapter(TOOL_TYPE_FUNCTION_TYPE,
						new MaterialMappedFunctionDeserializer<>(ToolType::get, ToolType::getName)).
				registerTypeAdapter(BlockRenderLayer.class, EnumDeserializer.INSTANCE).
				registerTypeAdapter(VoxelShape.class, VoxelShapeDeserializer.INSTANCE);
	}

	@Override
	public IBlockFormSettings deserializeSettings(JsonElement jsonElement, JsonDeserializationContext context) {
		return BlockFormSettingsDeserializer.INSTANCE.deserialize(jsonElement, context);
	}

	private static void createRegistryEntries() {
		for(IForm form : FORMS) {
			IBlockFormSettings settings = (IBlockFormSettings)form.getSettings();
			for(IMaterial material : form.getMaterials()) {
				boolean isMaterialDummy = material.getType().isDummy();
				String registryKey = isMaterialDummy ? material.getName()+form.getName() : form.getName()+'.'+material.getName();
				ResourceLocation registryName = new ResourceLocation(JAOPCA.MOD_ID, registryKey);

				IMaterialFormBlock materialFormBlock = settings.getBlockCreator().create(form, material, settings);
				Block block = materialFormBlock.asBlock();
				block.setRegistryName(registryName);
				BLOCKS.put(form, material, materialFormBlock);

				IMaterialFormBlockItem materialFormBlockItem = settings.getBlockItemCreator().create(materialFormBlock, settings);
				BlockItem blockItem = materialFormBlockItem.asBlockItem();
				blockItem.setRegistryName(registryName);
				BLOCK_ITEMS.put(form, material, materialFormBlockItem);

				if(!isMaterialDummy) {
					Supplier<Block> blockSupplier = ()->block;
					DataInjector.registerBlockTag(new ResourceLocation("forge", form.getSecondaryName()), blockSupplier);
					DataInjector.registerBlockTag(new ResourceLocation("forge", form.getSecondaryName()+'/'+material.getName()), blockSupplier);
					for(String alternativeName : material.getAlternativeNames()) {
						DataInjector.registerBlockTag(new ResourceLocation("forge", form.getSecondaryName()+'/'+alternativeName), blockSupplier);
					}

					Supplier<Item> itemSupplier = ()->blockItem;
					DataInjector.registerItemTag(new ResourceLocation("forge", form.getSecondaryName()), itemSupplier);
					DataInjector.registerItemTag(new ResourceLocation("forge", form.getSecondaryName()+'/'+material.getName()), itemSupplier);
					for(String alternativeName : material.getAlternativeNames()) {
						DataInjector.registerItemTag(new ResourceLocation("forge", form.getSecondaryName()+'/'+alternativeName), itemSupplier);
					}
				}
			}
		}
	}

	public static void registerBlocks(IForgeRegistry<Block> registry) {
		createRegistryEntries();
		for(IMaterialFormBlock block : BLOCKS.values()) {
			registry.register(block.asBlock());
		}
	}

	public static void registerItems(IForgeRegistry<Item> registry) {
		for(IMaterialFormBlockItem blockItem : BLOCK_ITEMS.values()) {
			registry.register(blockItem.asBlockItem());
		}
	}

	public static Collection<IMaterialFormBlock> getBlocks() {
		return BLOCKS.values();
	}

	public static Collection<IMaterialFormBlockItem> getBlockItems() {
		return BLOCK_ITEMS.values();
	}
}