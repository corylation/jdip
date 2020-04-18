//
//  @(#)World.java		4/2002
//
//  Copyright 2002 Zachary DelProposto. All rights reserved.
//  Use is subject to license terms.
//
//
//  This program is free software; you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation; either version 2 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program; if not, write to the Free Software
//  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
//  Or from http://www.gnu.org/
//
package dip.world;

import dip.world.metadata.ParticipantMetadata;
import dip.world.metadata.PlayerMetadata;
import dip.world.metadata.GameMetadata;
import dip.gui.undo.UndoRedoManager;

import dip.world.io.XMLSerializer;

import java.util.*;
import java.util.Map;

import java.net.*;
import java.util.zip.*;


/**
*	The entire game World. This contains the state of an entire game.
*	<p>
*	A World contains:
*	<ol>
*		<li>Map (dip.world.Map) object [constant]
*		<li>TurnState objects [in a linked hash map]
*	</ol>
*
*
*/
public class World
{
	// constants for non-turn-data lookup
	private static final String KEY_MODERATOR_METADATA = "_moderator_metadata_";
	private static final String KEY_WORLD_METADATA = "_world_metadata_";
	private static final String KEY_GAME_SETUP = "_game_setup_";
	private static final String KEY_VARIANT_INFO = "_variant_info_";
	
	// instance variables
	protected SortedMap 			turnStates = null;			// turn data
	protected Map 					nonTurnData = null;			// non-turn data (misc data & per-player data)
	protected final dip.world.Map	map;						// the actual map (constant)
	
	
	/**
	*	Constructs a World object.
	*/
	protected World(dip.world.Map map)
	{
		this.map = map;
		turnStates = Collections.synchronizedSortedMap(new TreeMap());
		nonTurnData = Collections.synchronizedMap(new HashMap(17));
	}// World()
	
	
	/** Returns the Map (dip.world.Map) associated with this World. */
	public dip.world.Map getMap()
	{
		return map;
	}// getMap()
	
	
	/** Convenience method: sets VictoryConditions from VariantInfo object. */
	public void setVictoryConditions(VictoryConditions value)
	{
		getVariantInfo().setVictoryConditions(value);
	}// setVictoryConditions()
	
	
	/** Convenience method: gets VictoryConditions from VariantInfo object. */
	public VictoryConditions getVictoryConditions()
	{
		return getVariantInfo().getVictoryConditions();
	}// getVictoryConditions()	
	
	
	/** Gets the first TurnState object */
	public TurnState getInitialTurnState()
	{
		TurnState ts = (TurnState) turnStates.get(turnStates.firstKey());
		if(ts != null)
		{
			ts.setWorld(this);
		}
		return ts;
	}// getInitialTurnState()
	
	
	/** Gets the most current (last in the list) TurnState. */
	public TurnState getLastTurnState()
	{
		TurnState ts = (TurnState) turnStates.get(turnStates.lastKey());
		if(ts != null)
		{
			ts.setWorld(this);
		}
		return ts;
	}// getLastTurnState()
	
	
	/** Gets the TurnState associated with the specified Phase */
	public TurnState getTurnState(Phase phase)
	{
		TurnState ts = (TurnState) turnStates.get(phase);
		if(ts != null)
		{
			ts.setWorld(this);
		}
		return ts;
	}// getTurnState()
	
	
	/** Gets the TurnState that comes after this phase (if it exists). 
	*	<p>
	*	Note that the next phase may not be (due to phase skipping) the
	*	same phase generated by phase.getNext(). This will return null
	*	iff we are at the last Phase.
	*/
	public TurnState getNextTurnState(TurnState state)
	{
		Phase current = state.getPhase();
		if(current == null)
		{
			return null;
		}
		
		Phase next = null;
		Iterator iter = turnStates.keySet().iterator();
		while(iter.hasNext())
		{
			Phase phase = (Phase) iter.next();
			if(current.compareTo(phase) == 0)
			{
				if(iter.hasNext())
				{
					next = (Phase) iter.next();
				}
				
				break;
			}
		}
		
		if(next == null)
		{
			return null;
		}
		
		TurnState ts = (TurnState) turnStates.get(next);
		ts.setWorld(this);
		return ts;
	}// getNextTurnState()
	
	
	/**
	*	Get all TurnStates. Note that the returned List
	*	may be modified, without modifications being reflected
	*	in the World object. However, modifications to individual
	*	TurnState objects will be reflected in the World object
	*	(TurnStates are not cloned here).
	*/
	public List getAllTurnStates()
	{
		Collection values = turnStates.values();
		ArrayList al = new ArrayList(values.size());
		al.addAll(values);
		return al;
	}// getAllTurnStates()
	
	
	/** Gets the TurnState that comes before the specified phase. 
	*	<p>
	*	Note that the previous phase may not be (due to phase skipping) the
	*	same phase generated by phase.getPrevious(). This will return null
	*	iff we are at the first (initial) Phase.
	*/
	public TurnState getPreviousTurnState(TurnState state)
	{
		Phase current = state.getPhase();
		if(current == null)
		{
			return null;
		}
		
		
		Phase previous = null;
		Iterator iter = turnStates.keySet().iterator();
		while(iter.hasNext())
		{
			Phase phase = (Phase) iter.next();
			if(phase.compareTo(current) != 0)
			{
				previous = phase;
			}
			else
			{
				break;
			}
		}
		
		if(previous == null)
		{
			return null;
		}
		
		TurnState ts = (TurnState) turnStates.get(previous);
		ts.setWorld(this);
		return ts;
	}// getPreviousTurnState()
	
	
	/** Determine if there is a previous TurnState to this. */
	public boolean hasPrevious(TurnState turnState)
	{
		if(turnState == null)
		{
			throw new IllegalArgumentException();
		}
		
		final Phase current = turnState.getPhase();
		Phase previous = null;
		Iterator iter = turnStates.keySet().iterator();
		while(iter.hasNext())
		{
			Phase phase = (Phase) iter.next();
			if(phase.compareTo(current) != 0)
			{
				previous = phase;
			}
			else
			{
				break;
			}
		}
		
		return (previous != null);
	}// hasPrevious()
	
	/** Determine if there is a next TurnState after this. */
	public boolean hasNext(TurnState turnState)
	{
		if(turnState == null)
		{
			throw new IllegalArgumentException();
		}
		
		final Phase current = turnState.getPhase();
		Phase next = null;
		Iterator iter = turnStates.keySet().iterator();
		while(iter.hasNext())
		{
			Phase phase = (Phase) iter.next();
			if(current.compareTo(phase) == 0)
			{
				if(iter.hasNext())
				{
					next = (Phase) iter.next();
				}
				
				break;
			}
		}
		
		return(next != null);
	}// hasNext()
	
	
	/** If a TurnState with the given phase already exists, it is replaced. */
	public void setTurnState(TurnState turnState)
	{
		turnStates.put(turnState.getPhase(), turnState);
	}// setTurnState()
	
	
	/** 
	*	Removes a turnstate from the world. This should 
	*	be used with caution!
	*/
	public void removeTurnState(TurnState turnState)
	{
		turnStates.remove(turnState.getPhase());
	}// removeTurnState()
	
	
	/** Removes <b>all</b> TurnStates from the World. */
	public void removeAllTurnStates()
	{
		turnStates.clear();
	}// removeAllTurnStates()
	
	
	/** returns sorted (ascending) set of all Phases */
	public Set getPhaseSet()
	{
		return turnStates.keySet();
	}// getPhaseSet()
	
	
	/** Sets the Game metadata */
	public void setGameMetadata(GameMetadata gmd)
	{
		if(gmd == null)
		{
			throw new IllegalArgumentException("null metadata");
		}
		
		nonTurnData.put(KEY_WORLD_METADATA, gmd);
	}// setGameMetadata()
	
	/** Gets the Game metadata. Never returns null. Does not return a copy. */
	public GameMetadata getGameMetadata()
	{
		GameMetadata gmd = (GameMetadata) nonTurnData.get(KEY_WORLD_METADATA);
		if(gmd == null)
		{
			gmd = new GameMetadata();
			setGameMetadata(gmd);
		}
		return gmd;
	}// setGameMetadata()
	
	
	/** Sets the metadata for a player, referenced by Power */
	public void setPlayerMetadata(Power power, PlayerMetadata pmd)
	{
		if(power == null || pmd == null)
		{
			throw new IllegalArgumentException();
		}
		
		// simple check
		if(!power.equals(pmd.getPower()))
		{
			throw new IllegalStateException("PlayerMetadata power != power argument");
		}
		
		nonTurnData.put(power, pmd);
	}// setPlayerMetadata()
	
	/** Gets the metadata for a power. Never returns null. Does not return a copy. */
	public PlayerMetadata getPlayerMetadata(Power power)
	{
		if(power == null)
		{
			throw new IllegalArgumentException();
		}
		
		PlayerMetadata pmd = (PlayerMetadata) nonTurnData.get(power);
		if(pmd == null)
		{
			pmd = new PlayerMetadata(power);
			setPlayerMetadata(power, pmd);
		}
		return pmd;
	}// getPlayerMetadata()
	
	/** Sets the metadata for a player, referenced by Power */
	public void setModeratorMetadata(ParticipantMetadata pmd)
	{
		if(pmd == null)
		{
			throw new IllegalArgumentException();
		}
		nonTurnData.put(KEY_MODERATOR_METADATA, pmd);
	}// setPlayerMetadata()
	
	/** Gets the metadata for a power. Never returns null. Does not return a copy. */
	public ParticipantMetadata getModeratorMetadata()
	{
		ParticipantMetadata pmd = (ParticipantMetadata) nonTurnData.get(KEY_MODERATOR_METADATA);
		if(pmd == null)
		{
			pmd = new ParticipantMetadata();
			pmd.setRole(ParticipantMetadata.ROLE_MODERATOR);
			setModeratorMetadata(pmd);
		}
		return pmd;
	}// getPlayerMetadata()
	
	/** Sets the GameSetup object. May be set to null. */
	public void setGameSetup(GameSetup gs)
	{
		nonTurnData.put(KEY_GAME_SETUP, gs);
	}// setGameSetup()
	
	
	/** Returns the GameSetup object. May return null. */
	public GameSetup getGameSetup()
	{
		return (GameSetup) nonTurnData.get(KEY_GAME_SETUP);
	}// getGameSetup()
	
	
	/** Get the Variant Info object. This returns a Reference to the Variant information. */
	public synchronized VariantInfo getVariantInfo()
	{
		VariantInfo vi = (VariantInfo) nonTurnData.get(KEY_VARIANT_INFO);
		
		if(vi == null)
		{
			vi = new VariantInfo();
			nonTurnData.put(KEY_VARIANT_INFO, vi);
		}
		
		return vi; 
	}// getVariantInfo()
	
	/** Set the Variant Info object. */
	public synchronized void setVariantInfo(VariantInfo vi)
	{
		nonTurnData.put(KEY_VARIANT_INFO, vi);
	}// getVariantInfo()
	
	
	/** Convenience method: gets RuleOptions from VariantInfo object. */
	public RuleOptions getRuleOptions()
	{
		return getVariantInfo().getRuleOptions();
	}// getRuleOptions()
	
	/** Convenience method: sets RuleOptions in VariantInfo object. */
	public void setRuleOptions(RuleOptions ruleOpts)
	{
		if(ruleOpts == null) { throw new IllegalArgumentException(); }
		getVariantInfo().setRuleOptions(ruleOpts);
	}// getRuleOptions()
	
	
	/** 
	*	Variant Info is a class which holds information about 
	*	the variant, map, symbols, and symbol options.
	*/
	public static class VariantInfo
	{
		private String variantName;
		private String mapName;
		private String symbolsName;
		private float variantVersion;
		private float symbolsVersion;
		private RuleOptions ruleOptions;
		private VictoryConditions victoryConditions;
		
		/** Create a VariantInfo object */
		public VariantInfo() {}
		
		/** Set the Variant name. */
		public synchronized void setVariantName(String value) 	{ this.variantName = value; }
		/** Set the Map name. */
		public synchronized void setMapName(String value) 		{ this.mapName = value; }
		/** Set the Symbol pack name. */
		public synchronized void setSymbolPackName(String value) 	{ this.symbolsName = value; }
		/** Set the Variant version. */
		public synchronized void setVariantVersion(float value) 	
		{ 
			checkVersion(value); 
			this.variantVersion = value; 
		}
		/** Set the Symbol pack version. */
		public synchronized void setSymbolPackVersion(float value)
		{ 
			checkVersion(value); 
			this.symbolsVersion = value; 
		}
		
		/** <b>Replaces</b> the current RuleOptions with the given RuleOptions */
		public synchronized void setRuleOptions(RuleOptions value)	
		{ 	
			if(value == null) { throw new IllegalArgumentException(); }
			ruleOptions = value; 
		}// setRuleOptions()
		
		/** <b>Replaces</b> the current VictoryConditions with the given VictoryConditions */
		public synchronized void setVictoryConditions(VictoryConditions value)
		{ 
			if(value == null) { throw new IllegalArgumentException(); }
			this.victoryConditions = value; 
		}// setVictoryConditions()
		
		/** Get the Variant name. */
		public synchronized String getVariantName() 		{ return this.variantName; }
		/** Get the Map name. */
		public synchronized String getMapName() 			{ return this.mapName; }
		/** Get the Symbol pack name. */
		public synchronized String getSymbolPackName() 		{ return this.symbolsName; }
		/** Get the Variant version. */
		public synchronized float getVariantVersion() 	{ return this.variantVersion; }
		/** Get the Symbol pack version. */
		public synchronized float getSymbolPackVersion() 	{ return this.symbolsVersion; }
		
		/** Gets the RuleOptions */
		public synchronized RuleOptions getRuleOptions()
		{
			if(ruleOptions == null)
			{
				ruleOptions = new RuleOptions();
			}
			
			return ruleOptions;
		}// getRuleOptions()
		
		/** Get the VictoryConditions */
		public synchronized VictoryConditions getVictoryConditions()
		{
			return victoryConditions;
		}// getVictoryConcitions()
		
		/** ensures Version is a value &gt;0.0f */
		private void checkVersion(float v)
		{
			if(v <= 0.0f)
			{
				throw new IllegalArgumentException("version: "+v);
			}
		}// checkVersion();
		
	}// nested class VariantInfo
	
}// class World
