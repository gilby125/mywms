/*
 * Copyright (c) 2010 LinogistiX GmbH
 * 
 *  www.linogistix.com
 *  
 *  Project myWMS-LOS
 */
package de.linogistix.mobileserver.processes.info;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.log4j.Logger;
import org.mywms.model.Client;

import de.linogistix.los.inventory.service.LOSCustomerOrderService;
import de.linogistix.los.inventory.service.LOSPickingPositionService;
import de.linogistix.los.inventory.service.QueryItemDataService;
import de.linogistix.los.inventory.service.QueryStockService;
import de.linogistix.los.model.State;
import de.linogistix.los.util.businessservice.ContextService;
import de.wms2.mywms.client.ClientBusiness;
import de.wms2.mywms.delivery.DeliveryOrder;
import de.wms2.mywms.delivery.DeliveryOrderLine;
import de.wms2.mywms.inventory.StockUnit;
import de.wms2.mywms.inventory.UnitLoad;
import de.wms2.mywms.inventory.UnitLoadEntityService;
import de.wms2.mywms.location.StorageLocation;
import de.wms2.mywms.location.StorageLocationEntityService;
import de.wms2.mywms.picking.PickingOrderLine;
import de.wms2.mywms.picking.PickingUnitLoad;
import de.wms2.mywms.picking.PickingUnitLoadEntityService;
import de.wms2.mywms.product.ItemData;
import de.wms2.mywms.strategy.FixAssignment;
import de.wms2.mywms.strategy.FixAssignmentEntityService;

/**
 * @author krane
 *
 */
@Stateless
public class InfoFacadeBean implements InfoFacade {
	Logger log = Logger.getLogger(InfoFacadeBean.class);
	
	@EJB
	private QueryItemDataService queryItemData;
	
	@EJB
	private QueryStockService queryStock;
	
	@EJB
	private FixAssignmentEntityService fixService;
	
	
	@EJB
	private ContextService contextService;
	@EJB
	private LOSPickingPositionService pickingPositionService;
	@EJB
	private LOSCustomerOrderService customerOrderService;

	@Inject
	private ClientBusiness clientService;
	@Inject
	private PickingUnitLoadEntityService pickinUnitLoadService;
	@Inject
	private StorageLocationEntityService locationService;
	@Inject
	private UnitLoadEntityService unitLoadService;

	@PersistenceContext(unitName = "myWMS")
	protected EntityManager manager;

	public Client getDefaultClient() {

		Client systemClient = clientService.getSingleClient();
		if (systemClient != null) {
			log.info("Only one client in system");
			return systemClient;
		}
		
		systemClient = clientService.getSystemClient();

		// Callers client not system-client
		Client callersClient = contextService.getCallersClient();
		if( !systemClient.equals(callersClient) ) {
			log.info("Caller is not system-client => only one client to use");
			return callersClient; 
		}
		
		
		log.info("Plenty clients");
		return null;
	}

	
	public InfoItemDataTO readItemData( String itemNumber ) {
		log.info("readItemData itemNumber="+itemNumber);
		ItemData item = queryItemData.getByItemNumber( itemNumber );
		
		List<FixAssignment> fixList = fixService.readList(item, null, null, null, null);

		List<StockUnit> suList = queryStock.getListByItemData(item, true);
		
		return new InfoItemDataTO(item, fixList, suList);
	}
	
	public InfoLocationTO readLocation( String locationName ) {
		log.info("readLocation locationName="+locationName);
		StorageLocation loc = null;
			loc = locationService.read(locationName);
		if( loc == null ) {
			return null;
		}
		
		List<FixAssignment> fixList = new ArrayList<>();
		FixAssignment fix = fixService.readFirst(null, loc);
		if( fix != null ) {
			fixList.add(fix);
		}

		InfoLocationTO locto = new InfoLocationTO( loc, fixList );
		if( locto.getNumUnitLoads() == 1 ) {
			readOrder( locto.getUnitLoad(), loc.getUnitLoads().get(0) );
		}
		
		return locto;
	}
	
	public List<InfoStockUnitTO> readStockUnitList( String itemNumber ) {
		log.info("readStockUnitList itemNumber="+itemNumber);
		
		List<InfoStockUnitTO> toList = new ArrayList<InfoStockUnitTO>();
		
		ItemData item = queryItemData.getByItemNumber( itemNumber );
		
		List<StockUnit> suList = queryStock.getListByItemData(item, true);
		if( suList == null || suList.size()<=0 ) {
			return toList;
		}
		
		for( StockUnit stock : suList ) {
			InfoStockUnitTO suto = new InfoStockUnitTO(stock);
			toList.add(suto);
		}
		
		return toList;
	}
	
	public List<InfoUnitLoadTO> readUnitLoadList( String locationName ) {
		log.info("readUnitLoadList locationName="+locationName);
		List<InfoUnitLoadTO> toList = new ArrayList<InfoUnitLoadTO>();
		StorageLocation loc = null;
		loc = locationService.read( locationName );
		if( loc == null ) {
			return toList;
		}
		
		for( UnitLoad ul : loc.getUnitLoads() ) {
			InfoUnitLoadTO ulto = new InfoUnitLoadTO(ul);
			readOrder(ulto, ul);
			
			toList.add(ulto);
			
		}
		
		return toList;
	}
	
	
	public InfoUnitLoadTO readUnitLoad( String label ) {
		log.info("readUnitLoad label="+label);
		UnitLoad ul = unitLoadService.read(label);
		if( ul == null ) {
			return null;
		}

		InfoUnitLoadTO ulto = new InfoUnitLoadTO(ul);
		readOrder(ulto, ul);
		return ulto;

	}

	private void readOrder( InfoUnitLoadTO ulto, UnitLoad ul ) {
		HashSet<DeliveryOrder> orderSetUl = new HashSet<DeliveryOrder>();
		HashSet<DeliveryOrder> pickSetUl = new HashSet<DeliveryOrder>();
		DeliveryOrder deliveryOrder = null;
		
		PickingUnitLoad pul = pickinUnitLoadService.getByLabel(ul.getLabelId());
		if( pul != null ) {
			orderSetUl.add( pul.getDeliveryOrder());
		}
		
		for( StockUnit su : ul.getStockUnitList() ) {
			HashSet<DeliveryOrder> orderSetSu = new HashSet<DeliveryOrder>();
			HashSet<DeliveryOrder> pickSetSu = new HashSet<DeliveryOrder>();
			
			if( deliveryOrder != null ) {
				orderSetSu.add( deliveryOrder );
			}
			else {
				List<PickingOrderLine> pickList = pickingPositionService.getByPickFromStockUnit(su);
				for( PickingOrderLine pick : pickList ) {
					if( pick.getState() < State.PICKED ) {
						DeliveryOrderLine orderPos = pick.getDeliveryOrderLine();
						if( orderPos != null ) {
							DeliveryOrder order = orderPos.getDeliveryOrder();
							pickSetSu.add( order );
							pickSetUl.add( order );
						}
					}
				}
			}
			
			InfoStockUnitTO suto = new InfoStockUnitTO( su ) ;
			
			for( DeliveryOrder order : orderSetSu ) {
				suto.getOrderList().add( new InfoOrderTO(order) );
			}
			for( DeliveryOrder order : pickSetSu ) {
				suto.getPickList().add( new InfoOrderTO(order) );
			}
			
			ulto.getStockUnitList().add(suto);
		}

		for( DeliveryOrder order : orderSetUl ) {
			ulto.getOrderList().add( new InfoOrderTO(order) );
		}
		for( DeliveryOrder order : pickSetUl ) {
			ulto.getPickList().add( new InfoOrderTO(order) );
		}
		
	}

}
