/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.edgetype;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.List;

import org.opentripplanner.common.geometry.PackedCoordinateSequence;
import org.opentripplanner.common.model.P2;
import org.opentripplanner.routing.algorithm.NegativeWeightException;
import org.opentripplanner.routing.core.AbstractEdge;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseOptions;
import org.opentripplanner.routing.core.TraverseResult;
import org.opentripplanner.routing.core.Vertex;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;

/**
 * This represents a street segment. This is unusual in an edge-based graph, but happens when we
 * have to split a set of turns to accommodate a transit stop.
 * 
 * @author novalis
 * 
 */
public class PlainStreetEdge extends AbstractEdge implements StreetEdge {

    private static final long serialVersionUID = 1L;

    private PackedCoordinateSequence elevationProfile;

    private double length;

    private LineString geometry;

    private String name;

    private double slopeSpeedEffectiveLength;

    private double bicycleSafetyEffectiveLength;

    private double slopeCostEffectiveLength;

    private boolean wheelchairAccessible = true;

    private double maxSlope;

    private StreetTraversalPermission permission;

    private String id;

    private boolean crossable = true;

    private boolean slopeOverride = false;

    public boolean back;

    public PlainStreetEdge(Vertex v1, Vertex v2, LineString geometry, String name, double length,
            StreetTraversalPermission permission, boolean back) {
        super(v1, v2);
        this.geometry = geometry;
        this.length = length;
        slopeSpeedEffectiveLength = length;
        bicycleSafetyEffectiveLength = length;
        slopeCostEffectiveLength = length;
        this.name = name;
        this.permission = permission;
        this.back = back;
    }

    @Override
    public boolean canTraverse(TraverseOptions options) {
        if (options.wheelchairAccessible) {
            if (!wheelchairAccessible) {
                return false;
            }
            if (maxSlope > options.maxSlope) {
                return false;
            }
        }

        if (options.getModes().getWalk() && permission.allows(StreetTraversalPermission.PEDESTRIAN)) {
            return true;
        }

        if (options.getModes().getBicycle() && permission.allows(StreetTraversalPermission.BICYCLE)) {
            return true;
        }

        if (options.getModes().getCar() && permission.allows(StreetTraversalPermission.CAR)) {
            return true;
        }

        return false;
    }

    @Override
    public PackedCoordinateSequence getElevationProfile() {
        return elevationProfile;
    }
    @Override
    public void setElevationProfile(PackedCoordinateSequence elev) {
        if (elev == null) {
            return;
        }
        if (slopeOverride) {
            elev = new PackedCoordinateSequence.Float(new Coordinate[] { elev.getCoordinate(0),elev.getCoordinate((elev.size()-1))},
                    2);
        }
        elevationProfile = elev;
        P2<Double> result = StreetVertex.computeSlopeCost(elev, getName());
        slopeCostEffectiveLength = result.getFirst();
        maxSlope = result.getSecond();
    }
    
    @Override
    public String getDirection() {
        return null;
    }

    @Override
    public double getDistance() {
        return length;
    }

    @Override
    public LineString getGeometry() {
        return geometry;
    }

    @Override
    public TraverseMode getMode() {
        return TraverseMode.WALK;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TraverseResult traverse(State s0, TraverseOptions options)
            throws NegativeWeightException {

        return doTraverse(s0, options, false);
    }

    private TraverseResult doTraverse(State s0, TraverseOptions options, boolean back) {
        if (!canTraverse(options)) {
            return tryWalkBike(s0, options, back);
        }

        State s1 = s0.clone();
        double time = length / options.speed;
        double weight;
        if (options.wheelchairAccessible) {
            weight = getSlopeSpeedEffectiveLength() / options.speed;
        } else if (options.getModes().contains(TraverseMode.BICYCLE)) {
            switch (options.optimizeFor) {
            case SAFE:
                weight = getBicycleSafetyEffectiveLength() / options.speed;
                break;
            case FLAT:
                weight = slopeCostEffectiveLength;
                break;
            case QUICK:
                weight = getSlopeSpeedEffectiveLength() / options.speed;
                break;
            default:
                // TODO: greenways
                weight = length / options.speed;
            }
        } else {
            weight = time;
        }
        weight *= options.distanceWalkFactor(s0.walkDistance + length / 2);
        weight *= options.walkReluctance;
        s1.walkDistance += length;
        s1.incrementTimeInSeconds((int) (back ? -time : time));
        return new TraverseResult(weight, s1, this);
    }

    private TraverseResult tryWalkBike(State s0, TraverseOptions options, boolean back) {
        if (options.getModes().contains(TraverseMode.BICYCLE)) {
            return doTraverse(s0, options.getWalkingOptions(), back);
        }
        return null;
    }

    @Override
    public TraverseResult traverseBack(State s0, TraverseOptions options)
            throws NegativeWeightException {
        return doTraverse(s0, options, true);
    }

    public void setSlopeSpeedEffectiveLength(double slopeSpeedEffectiveLength) {
        this.slopeSpeedEffectiveLength = slopeSpeedEffectiveLength;
    }

    public double getSlopeSpeedEffectiveLength() {
        return slopeSpeedEffectiveLength;
    }

    public void setBicycleSafetyEffectiveLength(double bicycleSafetyEffectiveLength) {
        this.bicycleSafetyEffectiveLength = bicycleSafetyEffectiveLength;
    }

    public double getBicycleSafetyEffectiveLength() {
        return bicycleSafetyEffectiveLength;
    }

    public double getLength() {
        return length;
    }

    public StreetTraversalPermission getPermission() {
        return permission;
    }

    public void setWheelchairAccessible(boolean wheelchairAccessible) {
        this.wheelchairAccessible = wheelchairAccessible;
    }

    public boolean isWheelchairAccessible() {
        return wheelchairAccessible;
    }

    public String getId() {
        return id;
    }

    public void writeObject(ObjectOutputStream out) throws IOException {
        id = null; // this is only used during graph construction
        out.writeObject(this);
    }

    public boolean isCrossable() {
        return crossable;
    }

    public boolean getSlopeOverride() {
        return slopeOverride ;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public PackedCoordinateSequence getElevationProfile(double start, double end) {
        if (elevationProfile == null) {
            return null;
        }
        List<Coordinate> coordList = new LinkedList<Coordinate>();

        if (start < 0)
            start = 0;
        if (end > length)
            end = length;

        for (Coordinate coord : elevationProfile.toCoordinateArray()) {
            if (coord.x >= start && coord.x <= end) {
                coordList.add(new Coordinate(coord.x - start, coord.y));
            }
        }

        Coordinate coordArr[] = new Coordinate[coordList.size()];
        return new PackedCoordinateSequence.Float(coordList.toArray(coordArr), 2);
    }

    public void setSlopeOverride(boolean slopeOverride) {
        this.slopeOverride = slopeOverride;
    }

}
