/*******************************************************************************
 Party Planner web application for guest seat assignments and entree choices
 Copyright (C) 2012  Aaron Mondelblatt
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 You should have received a copy of the GNU General Public License
 along with this program.  If not, see http://www.gnu.org/licenses/gpl-3.0.txt.
 ***********************************************************************************/
package com.muhlsoftware.wedding

import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.springframework.web.context.request.RequestContextHolder

class TableConfService {

	def getSortedGuestList(partyId) {
		def c = PartyGuest.createCriteria()
		return c{
			party{
				eq('id',partyId)
			}
			guest{
				order('lastName','asc')
				order('firstName','asc')
				order('middleName','asc')
			}
			isNull('seat')
		}
	}

	def editPartyGuest(params) {
		def g  = PartyGuest.findById(params?.id)
		if(g) {
			GrailsClassUtils.getPropertiesOfType(PartyGuest,Boolean)?.each{ p->
				g.putAt(p.name,false)
			}
			params?.each{ p ->
				def name = p.key
				if(g.hasProperty(name) && name != 'id' && name != 'version') {
					def v =  p.value
					if(GrailsClassUtils.isPropertyOfType(PartyGuest, name,Long)) {
						v = Long.valueOf(v)
					} else if ( GrailsClassUtils.isPropertyOfType(PartyGuest, name,Boolean) ) {
						if(v == "on") {
							v = true
						}
						v = Boolean.valueOf(v)
					}
					g.putAt(name, v)
				}
			}
			g.save(flush:true,,failOnError:true)
		}
		return g
	}

	def savePartyGuest(params) {
		def session = RequestContextHolder.currentRequestAttributes().getSession()
		def guest =  Guest.findById(params?.guest?.get('id'))
		if(!guest) {
			return ['status': 'FAILURE', "msg": "Guest Not Found"]
		}
		def party = Party.findById(session['partyId'])
		if(!party) {
			return ['status': 'FAILURE', "msg": "Party Not Found"]
		}
		if(PartyGuest.findByPartyAndGuest(party,guest) ) {
			return ['status': 'FAILURE', "msg": "Party Guest Already Exists"]
		}
		def newPG = new PartyGuest(party:party,guest:guest)
		GrailsClassUtils.getPropertiesOfType(PartyGuest,Boolean)?.each{ p->
			newPG.putAt(p.name,false)
		}
		params?.each{ p ->
			def name = p.key
			if(newPG.hasProperty(name) && name != 'id' && name != 'version' && name != 'guest') {
				def v =  p.value
				if(GrailsClassUtils.isPropertyOfType(PartyGuest, name,Long)) {
					v = Long.valueOf(v)
				} else if ( GrailsClassUtils.isPropertyOfType(PartyGuest, name,Boolean) ) {
					if(v == "on") {
						v = true
					}
					v = Boolean.valueOf(v)
				}

				newPG.putAt(name, v)
			}
		}
		newPG.save(flush:true,,failOnError:true)
		//associate this guest to the current party
		return['status': 'SUCCESS', "msg": "SUCCESS"]
	}

	def removePartyGuest(guestId) {
		def pg = PartyGuest.findById(guestId)
		if(pg){
			PartyGuest.withTransaction { status ->
				pg?.party?.partyGuests = null
				pg?.guest?.partyGuests = null
				pg?.party?.save(flush: true,failOnError:true)
				pg?.guest?.save(flush: true,failOnError:true)
				pg.seat?.partyGuest = null
				pg.seat?.save(flush:true,failOnError:true)
				this.removeGuestFromSeat(guestId) //make sure they aren sitting somewhere else
				pg.seat = null
				pg.delete(flush: true)
			}
		}
	}

	def addGuestToSeat(guestId,seatId) {
		def s = Seat.findById(seatId)
		if(!s) {
			return "No Seat Found"
		}
		if(s.partyGuest) {
			return "Already There"
		}
		def g = PartyGuest.findById(guestId)
		if(!g) {
			return "No Party Guest Found"
		}
		PartyGuest.withTransaction { status ->
			this.removeGuestFromSeat(guestId) //make sure they aren sitting somewhere else
			s?.partyGuest = g
			g?.seat = s
			g?.save(flush: true,failOnError:true)
			s?.save(flush: true,failOnError:true)
		}
		return "success"
	}

	def removeGuestFromSeat(guestId){
		def g = PartyGuest.findById(guestId)
		if(!g) {
			return "No Party Guest Found"
		}
		PartyGuest.withTransaction { status ->
			//findAnySeat with this guest
			def s = Seat.withCriteria {
				partyGuest{
					eq('id',g?.id)
				}
			}
			s?.each{
				it.partyGuest = null
				it.save(flush: true,failOnError:true)
			}
			g?.seat = null
			g?.save(flush: true,failOnError:true)
		}
		return "success"
	}
	
	
	def editTableName(tableId,tableName) {
		WedTable wedT = WedTable.findById(tableId)
		if(!wedT) {
			return "No Wedding Table Found"
		}
		def msg
		WedTable.withTransaction { status ->
			wedT.name = tableName
			if(wedT.validate()){
				wedT.save(flush:true,failOnError:true)
				msg = "success"
			} else {
				msg += "Error "
				wedT.errors.allErrors.each{
					msg += it?.toString()
				}
			}
		}
		return msg
	}
}