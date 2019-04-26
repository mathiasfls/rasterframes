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

package org.locationtech.rasterframes.ref

import RasterSource.{GDALRasterSource, JVMGeoTiffRasterSource}
import org.locationtech.rasterframes.TestData
import geotrellis.vector.Extent
import org.apache.spark.sql.rf.RasterSourceUDT
import org.locationtech.rasterframes.TestEnvironment
import org.locationtech.rasterframes.model.TileDimensions

/**
 *
 *
 * @since 8/22/18
 */
class RasterSourceSpec extends TestEnvironment with TestData {
  def sub(e: Extent) = {
    val c = e.center
    val w = e.width
    val h = e.height
    Extent(c.x, c.y, c.x + w * 0.1, c.y + h * 0.1)
  }

  describe("General RasterSource") {
    it("should identify as UDT") {
      assert(new RasterSourceUDT() === new RasterSourceUDT())
    }
    val rs = RasterSource(getClass.getResource("/L8-B8-Robinson-IL.tiff").toURI)
    it("should compute nominal tile layout bounds") {
      val bounds = rs.layoutBounds(TileDimensions(65, 60))
      val agg = bounds.reduce(_ combine _)
      agg should be (rs.gridBounds)
    }
    it("should compute nominal tile layout extents") {
      val extents = rs.layoutExtents(TileDimensions(63, 63))
      val agg = extents.reduce(_ combine _)
      agg should be (rs.extent)
    }
    it("should reassemble correct grid from extents") {
      val dims = TileDimensions(63, 63)
      val ext = rs.layoutExtents(dims).head
      val bounds = rs.layoutBounds(dims).head
      rs.rasterExtent.gridBoundsFor(ext, false) should be (bounds)
    }
  }

  describe("HTTP RasterSource") {
    it("should support metadata querying over HTTP") {
      withClue("remoteCOGSingleband") {
        val src = RasterSource(remoteCOGSingleband1)
        assert(!src.extent.isEmpty)
      }
      withClue("remoteCOGMultiband") {
        val src = RasterSource(remoteCOGMultiband)
        assert(!src.extent.isEmpty)
      }
    }
    it("should read sub-tile") {
      withClue("remoteCOGSingleband") {
        val src = RasterSource(remoteCOGSingleband1)
        val raster = src.read(sub(src.extent))
        assert(raster.size > 0 && raster.size < src.size)
      }
      withClue("remoteCOGMultiband") {
        val src = RasterSource(remoteCOGMultiband)
        //println("CoG size", src.size, src.dimensions)
        val raster = src.read(sub(src.extent))
        //println("Subtile size", raster.size, raster.dimensions)
        assert(raster.size > 0 && raster.size < src.size)
      }
    }
    it("should Java serialize") {
      import java.io._
      val src = RasterSource(remoteCOGSingleband1)
      val buf = new java.io.ByteArrayOutputStream()
      val out = new ObjectOutputStream(buf)
      out.writeObject(src)
      out.close()

      val data = buf.toByteArray
      val in = new ObjectInputStream(new ByteArrayInputStream(data))
      val recovered = in.readObject().asInstanceOf[RasterSource]
      assert(src.toString === recovered.toString)
    }
  }
  describe("File RasterSource") {
    it("should support metadata querying of file") {
      val localSrc = geotiffDir.resolve("LC08_B7_Memphis_COG.tiff").toUri
      val src = RasterSource(localSrc)
      assert(!src.extent.isEmpty)
    }
  }

  if(RasterSource.IsGDAL.hasGDAL) {
    describe("GDAL Rastersource") {
      val gdal = GDALRasterSource(cogPath)
      val jvm = JVMGeoTiffRasterSource(cogPath)
      it("should compute the same metadata as JVM RasterSource") {
        gdal.cellType should be(jvm.cellType)
      }
      it("should compute the same dimensions as JVM RasterSource") {
        val dims = TileDimensions(128, 128)
        gdal.extent should be(jvm.extent)
        gdal.rasterExtent should be(jvm.rasterExtent)
        gdal.cellSize should be(jvm.cellSize)
        gdal.layoutBounds(dims) should contain allElementsOf jvm.layoutBounds(dims)
        gdal.layoutExtents(dims) should contain allElementsOf jvm.layoutExtents(dims)
      }
    }
  }

  describe("RasterSourceToTiles Expression") {
    it("should read all tiles") {
      val src = RasterSource(remoteMODIS)

      val subrasters = src.readAll()

      val collected = subrasters.map(_.extent).reduceLeft(_.combine(_))

      assert(src.extent.xmin === collected.xmin +- 0.01)
      assert(src.extent.ymin === collected.ymin +- 0.01)
      assert(src.extent.xmax === collected.xmax +- 0.01)
      assert(src.extent.ymax === collected.ymax +- 0.01)

      val totalCells = subrasters.map(_.size).sum

      assert(totalCells === src.size)

//      subrasters.zipWithIndex.foreach{case (r, i) ⇒
//        // TODO: how to test?
//        GeoTiff(r, src.crs).write(s"target/$i.tiff")
//      }
    }
  }
}
