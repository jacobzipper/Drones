package com.parrot.sdksample.classes;

import com.parrot.sdksample.enums.Direction;

/**
 * Icarus
 * Pine Crest School
 * Contact:
 *  Email: jacob.zipper@pinecrest.edu
 *  Phone: 954-740-1737
 *
 * This class stores all the data you ned to know
 * about a wall in the search and rescue maze
 *
 */
public class Wall {
    public boolean dot; // true if a dot is on the wall and false if it is a person
    public String col; // string with the color of the wall
    public Direction dir; // direction of the wall in the coordinate
    public int[] coords; // coordinate of the wall
    public Wall(boolean d, String c, Direction di, int[] co) {
        dot = d;
        col = c;
        dir = di;
        coords = co;
    }
}
