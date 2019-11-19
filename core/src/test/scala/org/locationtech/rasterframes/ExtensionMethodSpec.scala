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

package org.locationtech.rasterframes

import geotrellis.proj4.LatLng
import geotrellis.raster.{ByteCellType, GridBounds, TileLayout}
import geotrellis.spark.tiling.{CRSWorldExtent, LayoutDefinition}
import geotrellis.spark.{KeyBounds, SpatialKey, TileLayerMetadata}
import org.apache.spark.sql.Encoders
import org.locationtech.rasterframes.util._

import scala.xml.parsing.XhtmlParser

/**
 * Tests miscellaneous extension methods.
 *
 * @since 3/20/18
 */
//noinspection ScalaUnusedSymbol
class ExtensionMethodSpec extends TestEnvironment with TestData with SubdivideSupport {
  lazy val rf = sampleTileLayerRDD.toLayer

  describe("DataFrame extension methods") {
    it("should maintain original type") {
      val df = rf.withPrefixedColumnNames("_foo_")
      "val rf2: RasterFrameLayer = df" should compile
    }
    it("should provide tagged column access") {
      val df = rf.drop("tile")
      "val Some(col) = df.spatialKeyColumn" should compile
    }
  }
  describe("RasterFrameLayer extension methods") {
    it("should provide spatial key column") {
      noException should be thrownBy {
        rf.spatialKeyColumn
      }
      "val Some(col) = rf.spatialKeyColumn" shouldNot compile
    }
  }
  describe("Miscellaneous extensions") {
    import spark.implicits._

    it("should find multiple extent columns") {
      val df = Seq((extent, "fred", extent, 34.0)).toDF("e1", "s", "e2", "n")
      df.extentColumns.size should be(2)
    }

    it("should find multiple crs columns") {
      // Not sure why implicit resolution isn't handling this properly.
      implicit val enc = Encoders.tuple(crsEncoder, Encoders.STRING, crsEncoder, Encoders.scalaDouble)
      val df = Seq((pe.crs, "fred", pe.crs, 34.0)).toDF("c1", "s", "c2", "n")
      df.crsColumns.size should be(2)
    }

    it("should split TileLayout") {
      val tl1 = TileLayout(2, 3, 10, 10)
      assert(tl1.subdivide(0) === tl1)
      assert(tl1.subdivide(1) === tl1)
      assert(tl1.subdivide(2) === TileLayout(4, 6, 5, 5))
      assertThrows[IllegalArgumentException](tl1.subdivide(-1))
    }

    it("should split KeyBounds[SpatialKey]") {
      val grid = GridBounds(0, 0, 9, 9)
      val kb = KeyBounds(grid)
      val kb2 = kb.subdivide(2)
      assert(kb2.get.toGridBounds() === GridBounds(0, 0, 19, 19))

      val grid2 = GridBounds(2, 2, 9, 9)
      val kb3 = KeyBounds(grid2)
      val kb4 = kb3.subdivide(2)
      assert(kb4.get.toGridBounds() === GridBounds(4, 4, 19, 19))
    }

    it("should split key") {
      val s1 = SpatialKey(0, 0).subdivide(2)
      assert(s1 === Seq(SpatialKey(0, 0), SpatialKey(1, 0), SpatialKey(0, 1), SpatialKey(1, 1)))

      val s2 = SpatialKey(2, 3).subdivide(3)
      assert(s2 === Seq(SpatialKey(6, 9), SpatialKey(7, 9), SpatialKey(8, 9), SpatialKey(6, 10), SpatialKey(7, 10), SpatialKey(8, 10), SpatialKey(6, 11), SpatialKey(7, 11), SpatialKey(8, 11)))
    }

    it("should split TileLayerMetadata[SpatialKey]") {
      val tileSize = 12
      val dataGridSize = 2
      val grid = GridBounds(2, 4, 10, 11)
      val layout = LayoutDefinition(LatLng.worldExtent, TileLayout(dataGridSize, dataGridSize, tileSize, tileSize))
      val tlm = TileLayerMetadata(ByteCellType, layout, LatLng.worldExtent, LatLng, KeyBounds(grid))

      val divided = tlm.subdivide(2)

      assert(divided.tileLayout.tileDimensions === (tileSize / 2, tileSize / 2))
    }

    it("should render Markdown") {
      import org.apache.spark.sql.functions.lit

      val md = rf.toMarkdown()
      md.count(_ == '|') shouldBe >=(3 * 5)
      md.count(_ == '\n') should be >= 6

      val md2 = rf.withColumn("long_string", lit("p" * 42)).toMarkdown(truncate=true, renderTiles = false)
      md2 should include ("...")

      val md3 = rf.toMarkdown(truncate=true, renderTiles = false)
      md3 shouldNot include("<img")

      // Should truncate JTS types even when we don't ask for it.
      val md4 = rf.withGeometry().select("geometry").toMarkdown(truncate = false)
      md4 should include ("...")
    }

    it("should render HTML") {
      val html = rf.toHTML(renderTiles = false)
      noException shouldBe thrownBy {
        XhtmlParser(scala.io.Source.fromString(html))
      }
      val html2 = rf.toHTML(renderTiles = true)
      noException shouldBe thrownBy {
        XhtmlParser(scala.io.Source.fromString(html2))
      }
    }
  }
}
