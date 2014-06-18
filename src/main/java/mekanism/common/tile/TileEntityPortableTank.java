package mekanism.common.tile;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;

import mekanism.api.Coord4D;
import mekanism.api.IConfigurable;
import mekanism.common.IActiveState;
import mekanism.common.ISustainedTank;
import mekanism.common.Mekanism;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.PipeUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

public class TileEntityPortableTank extends TileEntityContainerBlock implements IActiveState, IConfigurable, IFluidHandler, ISustainedTank
{
	public boolean isActive;

	public boolean clientActive;
	
	public FluidTank fluidTank = new FluidTank(14000);
	
	public int updateDelay;
	
	public TileEntityPortableTank() 
	{
		super("PortableTank");
		
		inventory = new ItemStack[2];
	}
	
	@Override
	public boolean canSetFacing(int facing)
	{
		return false;
	}

	@Override
	public void onUpdate() 
	{
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
			if(inventory[0] != null)
			{
				manageInventory();
			}
			
			if(isActive)
			{
				activeEmit();
			}
		}
	}
	
	private void activeEmit()
	{
		if(fluidTank.getFluid() != null)
		{
			TileEntity tileEntity = Coord4D.get(this).getFromSide(ForgeDirection.DOWN).getTileEntity(worldObj);

			if(tileEntity instanceof IFluidHandler)
			{
				FluidStack toDrain = new FluidStack(fluidTank.getFluid(), Math.min(100, fluidTank.getFluidAmount()));
				fluidTank.drain(((IFluidHandler)tileEntity).fill(ForgeDirection.UP, toDrain, true), true);
			}
		}
	}
	
	private void manageInventory()
	{
		if(inventory[0] != null)
		{
			if(FluidContainerRegistry.isEmptyContainer(inventory[0]))
			{
				if(fluidTank.getFluid() != null && fluidTank.getFluid().amount >= FluidContainerRegistry.BUCKET_VOLUME)
				{
					ItemStack filled = FluidContainerRegistry.fillFluidContainer(fluidTank.getFluid(), inventory[0]);

					if(filled != null)
					{
						if(inventory[1] == null || (inventory[1].isItemEqual(filled) && inventory[1].stackSize+1 <= filled.getMaxStackSize()))
						{
							inventory[0].stackSize--;

							if(inventory[0].stackSize <= 0)
							{
								inventory[0] = null;
							}

							if(inventory[1] == null)
							{
								inventory[1] = filled;
							}
							else {
								inventory[1].stackSize++;
							}

							fluidTank.drain(FluidContainerRegistry.getFluidForFilledItem(filled).amount, true);
						}
					}
				}
			}
			else if(FluidContainerRegistry.isFilledContainer(inventory[0]))
			{
				FluidStack itemFluid = FluidContainerRegistry.getFluidForFilledItem(inventory[0]);

				if((fluidTank.getFluid() == null && itemFluid.amount <= fluidTank.getCapacity()) || fluidTank.getFluid().amount+itemFluid.amount <= fluidTank.getCapacity())
				{
					if(fluidTank.getFluid() != null && !fluidTank.getFluid().isFluidEqual(itemFluid))
					{
						return;
					}

					ItemStack containerItem = inventory[0].getItem().getContainerItem(inventory[0]);

					boolean filled = false;

					if(containerItem != null)
					{
						if(inventory[1] == null || (inventory[1].isItemEqual(containerItem) && inventory[1].stackSize+1 <= containerItem.getMaxStackSize()))
						{
							inventory[0] = null;

							if(inventory[1] == null)
							{
								inventory[1] = containerItem;
							}
							else {
								inventory[1].stackSize++;
							}

							filled = true;
						}
					}
					else {
						inventory[0].stackSize--;

						if(inventory[0].stackSize == 0)
						{
							inventory[0] = null;
						}

						filled = true;
					}

					if(filled)
					{
						fluidTank.fill(itemFluid, true);
					}
				}
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTags)
	{
		super.writeToNBT(nbtTags);

		nbtTags.setBoolean("isActive", isActive);
		
		if(fluidTank.getFluid() != null)
		{
			nbtTags.setTag("fluidTank", fluidTank.writeToNBT(new NBTTagCompound()));
		}
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbtTags)
	{
		super.readFromNBT(nbtTags);

		clientActive = isActive = nbtTags.getBoolean("isActive");
		
		if(nbtTags.hasKey("fluidTank"))
		{
			fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
		}
	}
	
	@Override
	public void handlePacketData(ByteBuf dataStream)
	{
		super.handlePacketData(dataStream);

		clientActive = dataStream.readBoolean();
		
		if(dataStream.readInt() == 1)
		{
			fluidTank.setFluid(new FluidStack(dataStream.readInt(), dataStream.readInt()));
		}
		else {
			fluidTank.setFluid(null);
		}
		
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
		
		if(fluidTank.getFluid() != null)
		{
			data.add(1);
			data.add(fluidTank.getFluid().fluidID);
			data.add(fluidTank.getFluid().amount);
		}
		else {
			data.add(0);
		}
		
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
	public boolean onSneakRightClick(EntityPlayer player, int side)
	{
		setActive(!getActive());
		worldObj.playSoundEffect(xCoord, yCoord, zCoord, "random.click", 0.3F, 1);
		return true;
	}

	@Override
	public boolean onRightClick(EntityPlayer player, int side)
	{
		return false;
	}

	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill) 
	{
		if(resource != null && canFill(from, resource.getFluid()))
		{
			return fluidTank.fill(resource, doFill);
		}
		
		return 0;
	}

	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) 
	{
		if(resource != null && canDrain(from, resource.getFluid()))
		{
			return fluidTank.drain(resource.amount, doDrain);
		}
		
		return null;
	}

	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) 
	{
		if(canDrain(from, null))
		{
			return fluidTank.drain(maxDrain, doDrain);
		}
		
		return null;
	}

	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid) 
	{
		return from == ForgeDirection.UP && (fluidTank.getFluid() == null || fluidTank.getFluid().getFluid() == fluid);
	}

	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid)
	{
		return !isActive && from == ForgeDirection.DOWN && (fluid == null || (fluidTank != null && fluidTank.getFluid().getFluid() == fluid));
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from) 
	{
		if(from == ForgeDirection.UP || from == ForgeDirection.DOWN)
		{
			return new FluidTankInfo[] {fluidTank.getInfo()};
		}
		
		return PipeUtils.EMPTY;
	}
	
	@Override
	public void setFluidStack(FluidStack fluidStack, Object... data)
	{
		fluidTank.setFluid(fluidStack);
	}

	@Override
	public FluidStack getFluidStack(Object... data)
	{
		return fluidTank.getFluid();
	}

	@Override
	public boolean hasTank(Object... data)
	{
		return true;
	}
}