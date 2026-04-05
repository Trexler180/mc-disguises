package com.coffee.disguises.watcher;

import net.minecraft.core.Rotations;

/**
 * ArmorStand-specific metadata: size, arms, base plate, marker, and 6 pose angles.
 *
 * Vanilla ArmorStand DataTracker (Mojang 1.21.x):
 *   (extends LivingEntity DIRECTLY — no Mob layer, so entity-specific fields start at 15)
 *   index 15 - client flags byte:
 *                bit 0 (0x01) = small
 *                bit 2 (0x04) = show arms
 *                bit 3 (0x08) = no base plate
 *                bit 4 (0x10) = is marker (no hitbox / invisible)
 *   index 16 - head pose    (Rotations)
 *   index 17 - body pose    (Rotations)
 *   index 18 - left arm pose  (Rotations)
 *   index 19 - right arm pose (Rotations)
 *   index 20 - left leg pose  (Rotations)
 *   index 21 - right leg pose (Rotations)
 */
public class ArmorStandWatcher extends LivingEntityWatcher {

    private boolean   small        = false;
    private boolean   showArms     = false;
    private boolean   noBasePlate  = false;
    private boolean   marker       = false;

    private Rotations headPose      = new Rotations(0, 0, 0);
    private Rotations bodyPose      = new Rotations(0, 0, 0);
    private Rotations leftArmPose   = new Rotations(-10, 0, -10);
    private Rotations rightArmPose  = new Rotations(-15, 0, 10);
    private Rotations leftLegPose   = new Rotations(1, 0, -1);
    private Rotations rightLegPose  = new Rotations(1, 0, 1);

    public ArmorStandWatcher setSmall(boolean small)            { this.small       = small;       return this; }
    public ArmorStandWatcher setShowArms(boolean showArms)      { this.showArms    = showArms;    return this; }
    public ArmorStandWatcher setNoBasePlate(boolean noBase)     { this.noBasePlate = noBase;      return this; }
    public ArmorStandWatcher setMarker(boolean marker)          { this.marker      = marker;      return this; }

    public ArmorStandWatcher setHeadPose(float x, float y, float z)      { headPose      = new Rotations(x, y, z); return this; }
    public ArmorStandWatcher setBodyPose(float x, float y, float z)      { bodyPose      = new Rotations(x, y, z); return this; }
    public ArmorStandWatcher setLeftArmPose(float x, float y, float z)   { leftArmPose   = new Rotations(x, y, z); return this; }
    public ArmorStandWatcher setRightArmPose(float x, float y, float z)  { rightArmPose  = new Rotations(x, y, z); return this; }
    public ArmorStandWatcher setLeftLegPose(float x, float y, float z)   { leftLegPose   = new Rotations(x, y, z); return this; }
    public ArmorStandWatcher setRightLegPose(float x, float y, float z)  { rightLegPose  = new Rotations(x, y, z); return this; }

    public boolean isSmall()       { return small; }
    public boolean isShowArms()    { return showArms; }
    public boolean isNoBasePlate() { return noBasePlate; }
    public boolean isMarker()      { return marker; }

    public Rotations getHeadPose()      { return headPose; }
    public Rotations getBodyPose()      { return bodyPose; }
    public Rotations getLeftArmPose()   { return leftArmPose; }
    public Rotations getRightArmPose()  { return rightArmPose; }
    public Rotations getLeftLegPose()   { return leftLegPose; }
    public Rotations getRightLegPose()  { return rightLegPose; }

    /** Packs all boolean flags into the vanilla ArmorStand flags byte. */
    public byte getArmorStandFlags() {
        byte b = 0;
        if (small)       b |= 0x01;
        if (showArms)    b |= 0x04;
        if (noBasePlate) b |= 0x08;
        if (marker)      b |= 0x10;
        return b;
    }
}
