package mekanism.common.tile;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.EnumSet;

import mekanism.api.Coord4D;
import mekanism.common.IActiveState;
import mekanism.common.IRedstoneControl;
import mekanism.common.Mekanism;
import mekanism.common.block.BlockMachine.MachineType;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.util.ChargeUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntitySeismicVibrator extends TileEntityElectricBlock implements IActiveState, IRedstoneControl
{
	public boolean isActive;

	public boolean clientActive;
	
	public int updateDelay;
	
	public float clientPiston;
	
	public RedstoneControl controlType = RedstoneControl.DISABLED;
	
	public TileEntitySeismicVibrator()
	{
		super("SeismicVibrator", MachineType.SEISMIC_VIBRATOR.baseEnergy);
		
		inventory = new ItemStack[1];
	}
	
	@Override
	public void onUpdate()
	{
		super.onUpdate();
		
		if(worldObj.isRemote)
		{
			if(updateDelay > 0)
			{
				updateDelay--;

				if(updateDelay == 0 && clientActive != isActive)
				{
					isActive = clientActive;
					MekanismUtils.updateBlock(worldObj, xCoord, yCoord, zCoord);
				}
			}
		}
		else {
			if(updateDelay > 0)
			{
				updateDelay--;

				if(updateDelay == 0 && clientActive != isActive)
				{
					Mekanism.packetHandler.sendToAll(new TileEntityMessage(Coord4D.get(this), getNetworkedData(new ArrayList())));
				}
			}
			
			ChargeUtils.discharge(0, this);
			
			if(MekanismUtils.canFunction(this) && getEnergy() >= Mekanism.seismicVibratorUsage)
			{
				setActive(true);
				setEnergy(getEnergy()-Mekanism.seismicVibratorUsage);
			}
			else {
				setActive(false);
			}
		}
		
		if(getActive())
		{
			Mekanism.activeVibrators.add(Coord4D.get(this));
		}
		else {
			Mekanism.activeVibrators.remove(Coord4D.get(this));
		}
	}
	
	@Override
	public void invalidate()
	{
		super.invalidate();
		
		Mekanism.activeVibrators.remove(Coord4D.get(this));
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbtTags)
	{
		super.writeToNBT(nbtTags);

		nbtTags.setBoolean("isActive", isActive);
		nbtTags.setInteger("controlType", controlType.ordinal());
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbtTags)
	{
		super.readFromNBT(nbtTags);

		clientActive = isActive = nbtTags.getBoolean("isActive");
		controlType = RedstoneControl.values()[nbtTags.getInteger("controlType")];
	}
	
	@Override
	public void handlePacketData(ByteBuf dataStream)
	{
		super.handlePacketData(dataStream);

		clientActive = dataStream.readBoolean();
		controlType = RedstoneControl.values()[dataStream.readInt()];
		
		if(updateDelay == 0 && clientActive != isActive)
		{
			updateDelay = Mekanism.UPDATE_DELAY;
			isActive = clientActive;
			MekanismUtils.updateBlock(worldObj, xCoord, yCoord, zCoord);
		}
	}
	
	@Override
	public ArrayList getNetworkedData(ArrayList data)
	{
		super.getNetworkedData(data);

		data.add(isActive);
		data.add(controlType.ordinal());
		
		return data;
	}
	
	@Override
	public void setActive(boolean active)
	{
		isActive = active;

		if(clientActive != active && updateDelay == 0)
		{
			Mekanism.packetHandler.sendToAll(new TileEntityMessage(Coord4D.get(this), getNetworkedData(new ArrayList())));

			updateDelay = 10;
			clientActive = active;
		}
	}

	@Override
	public boolean getActive()
	{
		return isActive;
	}
	
	@Override
	public boolean renderUpdate()
	{
		return false;
	}

	@Override
	public boolean lightUpdate()
	{
		return true;
	}
	
	@Override
	public boolean canSetFacing(int facing)
	{
		return facing != 0 && facing != 1;
	}
	
	@Override
	public RedstoneControl getControlType()
	{
		return controlType;
	}
	
	@Override
	protected EnumSet<ForgeDirection> getConsumingSides()
	{
		return EnumSet.of(ForgeDirection.UP);
	}

	@Override
	public void setControlType(RedstoneControl type)
	{
		controlType = type;
		MekanismUtils.saveChunk(this);
	}
}
