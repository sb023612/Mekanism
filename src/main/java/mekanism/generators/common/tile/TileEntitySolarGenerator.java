package mekanism.generators.common.tile;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.EnumSet;

import mekanism.common.Mekanism;
import mekanism.common.util.ChargeUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.common.MekanismGenerators;
import micdoodle8.mods.galacticraft.api.world.ISolarLevel;
import net.minecraft.item.ItemStack;
import net.minecraft.world.biome.BiomeGenDesert;
import net.minecraftforge.common.util.ForgeDirection;
import cpw.mods.fml.common.ModAPIManager;
import cpw.mods.fml.common.Optional.Method;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;

public class TileEntitySolarGenerator extends TileEntityGenerator
{
	/** Whether or not this generator sees the sun. */
	public boolean seesSun = false;

	/** How fast this tile entity generates energy. */
	public double GENERATION_RATE;

	public TileEntitySolarGenerator()
	{
		super("SolarGenerator", 96000, MekanismGenerators.solarGeneration*2);
		GENERATION_RATE = MekanismGenerators.solarGeneration;
		inventory = new ItemStack[1];
	}

	public TileEntitySolarGenerator(String name, double maxEnergy, double output)
	{
		super(name, maxEnergy, output);
		inventory = new ItemStack[1];
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side)
	{
		return new int[] {0};
	}

	@Override
	public float getVolumeMultiplier()
	{
		return 0.05F;
	}

	@Override
	public boolean canSetFacing(int facing)
	{
		return facing != 0 && facing != 1;
	}

	@Override
	public void onUpdate()
	{
		super.onUpdate();

		ChargeUtils.charge(0, this);

		if(!worldObj.isRemote)
		{
			if(worldObj.isDaytime() && ((!worldObj.isRaining() && !worldObj.isThundering()) || isDesert()) && !worldObj.provider.hasNoSky && worldObj.canBlockSeeTheSky(xCoord, yCoord+1, zCoord))
			{
				seesSun = true;
			}
			else {
				seesSun = false;
			}

			if(canOperate())
			{
				setActive(true);
				setEnergy(getEnergy() + getProduction());
			}
			else {
				setActive(false);
			}
		}
	}

	public boolean isDesert()
	{
		return worldObj.provider.getBiomeGenForCoords(xCoord >> 4, zCoord >> 4) instanceof BiomeGenDesert;
	}

	@Override
	public boolean canExtractItem(int slotID, ItemStack itemstack, int side)
	{
		if(slotID == 0)
		{
			return ChargeUtils.canBeOutputted(itemstack, true);
		}

		return false;
	}

	@Override
	public boolean isItemValidForSlot(int slotID, ItemStack itemstack)
	{
		if(slotID == 0)
		{
			return ChargeUtils.canBeCharged(itemstack);
		}

		return true;
	}

	@Override
	public boolean canOperate()
	{
		return getEnergy() < getMaxEnergy() && seesSun && MekanismUtils.canFunction(this);
	}

	public double getProduction()
	{
		double ret = 0;

		if(seesSun)
		{
			ret = GENERATION_RATE;

			if(ModAPIManager.INSTANCE.hasAPI("Galacticraft API") && worldObj.provider instanceof ISolarLevel)
			{
				ret *= ((ISolarLevel)worldObj.provider).getSolarEnergyMultiplier();
			}

			if(isDesert())
			{
				ret *= 1.5;
			}

			return ret;
		}

		return 0;
	}

	@Override
	@Method(modid = "ComputerCraft")
	public String[] getMethodNames()
	{
		return new String[] {"getStored", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getSeesSun"};
	}

	@Override
	@Method(modid = "ComputerCraft")
	public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) throws Exception
	{
		switch(method)
		{
			case 0:
				return new Object[] {electricityStored};
			case 1:
				return new Object[] {output};
			case 2:
				return new Object[] {MAX_ELECTRICITY};
			case 3:
				return new Object[] {(MAX_ELECTRICITY-electricityStored)};
			case 4:
				return new Object[] {seesSun};
			default:
				Mekanism.logger.error("Attempted to call unknown method with computer ID " + computer.getID());
				return null;
		}
	}

	@Override
	public void handlePacketData(ByteBuf dataStream)
	{
		super.handlePacketData(dataStream);
		seesSun = dataStream.readBoolean();
	}

	@Override
	public ArrayList getNetworkedData(ArrayList data)
	{
		super.getNetworkedData(data);
		data.add(seesSun);
		return data;
	}

	@Override
	public EnumSet<ForgeDirection> getOutputtingSides()
	{
		return EnumSet.of(ForgeDirection.getOrientation(0));
	}

	@Override
	public boolean renderUpdate()
	{
		return false;
	}

	@Override
	public boolean lightUpdate()
	{
		return false;
	}
}
