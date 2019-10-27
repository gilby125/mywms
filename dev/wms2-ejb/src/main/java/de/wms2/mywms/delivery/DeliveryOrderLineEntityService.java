/* 
Copyright 2019 Matthias Krane
info@krane.engineer

This file is part of the Warehouse Management System mywms

mywms is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.
 
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
*/
package de.wms2.mywms.delivery;

import java.math.BigDecimal;

import javax.ejb.Stateless;
import javax.inject.Inject;

import de.wms2.mywms.entity.PersistenceManager;
import de.wms2.mywms.exception.BusinessException;
import de.wms2.mywms.inventory.Lot;
import de.wms2.mywms.product.ItemData;
import de.wms2.mywms.sequence.SequenceBusiness;
import de.wms2.mywms.strategy.OrderState;

/**
 *
 * @author krane
 */
@Stateless
public class DeliveryOrderLineEntityService {

	@Inject
	private PersistenceManager manager;
	@Inject
	private SequenceBusiness sequenceBusiness;

	public DeliveryOrderLine create(DeliveryOrder order, ItemData item, Lot lot, BigDecimal amount, int startCounter)
			throws BusinessException {
		String orderNumber = order.getOrderNumber();
		String lineNumber = sequenceBusiness.readNextCounterValue(orderNumber, startCounter, DeliveryOrderLine.class,
				"lineNumber");

		DeliveryOrderLine orderLine = manager.createInstance(DeliveryOrderLine.class);
		orderLine.setItemData(item);
		orderLine.setLot(lot);
		orderLine.setAmount(amount);
		orderLine.setClient(order.getClient());
		orderLine.setLineNumber(lineNumber);
		orderLine.setDeliveryOrder(order);
		orderLine.setState(OrderState.CREATED);

		manager.persist(orderLine);

		order.getLines().add(orderLine);

		return orderLine;
	}

}
