/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright 2018 Astraea, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     [http://www.apache.org/licenses/LICENSE-2.0]
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.locationtech.rasterframes.jts

import org.locationtech.jts.geom.{CoordinateSequence, Envelope, Geometry, GeometryFactory, Point}
import org.locationtech.jts.geom.util.GeometryTransformer
import geotrellis.proj4.CRS
import geotrellis.vector.Extent
import org.locationtech.jts.algorithm.Centroid

/**
 * JTS Geometry reprojection transformation routine.
 *
 * @since 6/4/18
 */
class ReprojectionTransformer(src: CRS, dst: CRS) extends GeometryTransformer {
  lazy val transform = geotrellis.proj4.Transform(src, dst)
  @transient
  private lazy val gf = new GeometryFactory()
  def apply(geometry: Geometry): Geometry = transform(geometry)
  def apply(extent: Extent): Geometry = transform(extent.jtsGeom)
  def apply(env: Envelope): Geometry = transform(gf.toGeometry(env))
  def apply(pt: Point): Point = {
    val t = transform(pt)
    gf.createPoint(Centroid.getCentroid(t))
  }

  override def transformCoordinates(coords: CoordinateSequence, parent: Geometry): CoordinateSequence = {
    val fact = parent.getFactory
    val retval = fact.getCoordinateSequenceFactory.create(coords)
    for(i <- 0 until coords.size()) {
      val x = coords.getOrdinate(i, CoordinateSequence.X)
      val y = coords.getOrdinate(i, CoordinateSequence.Y)
      val (xp, yp) = transform(x, y)
      retval.setOrdinate(i, CoordinateSequence.X, xp)
      retval.setOrdinate(i, CoordinateSequence.Y, yp)
    }
    retval
  }
}
