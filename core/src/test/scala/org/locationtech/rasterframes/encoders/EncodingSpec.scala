/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright 2019 Astraea, Inc.
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

package org.locationtech.rasterframes.encoders

import java.io.File
import java.net.URI

import org.locationtech.rasterframes._
import org.locationtech.jts.geom.Envelope
import geotrellis.proj4._
import geotrellis.raster.{CellType, Tile, TileFeature}
import geotrellis.spark.{SpaceTimeKey, SpatialKey, TemporalProjectedExtent, TileLayerMetadata}
import geotrellis.vector.{Extent, ProjectedExtent}
import org.apache.spark.sql.Row
import org.apache.spark.sql.functions._
import org.apache.spark.sql.rf.TileUDT
import org.locationtech.rasterframes.TestEnvironment
import org.locationtech.rasterframes.tiles.ProjectedRasterTile

/**
 * Test rig for encoding GT types into Catalyst types.
 *
 * @since 9/18/17
 */
class EncodingSpec extends TestEnvironment with TestData {

  import spark.implicits._

  describe("Spark encoding on standard types") {

    it("should serialize Tile") {
      val TileType = new TileUDT()

      forAll(allTileTypes) { t =>
        noException shouldBe thrownBy {
          TileType.deserialize(TileType.serialize(t))
        }
      }
    }

    it("should code RDD[Tile]") {
      val rdd = sc.makeRDD(Seq(byteArrayTile: Tile, null))
      val ds = rdd.toDF("tile")
      write(ds)
      assert(ds.toDF.as[Tile].collect().head === byteArrayTile)
    }

    it("should code RDD[(Int, Tile)]") {
      val ds = Seq((1, byteArrayTile: Tile), (2, null)).toDS
      write(ds)
      assert(ds.toDF.as[(Int, Tile)].collect().head === ((1, byteArrayTile)))
    }

    it("should code RDD[TileFeature]") {
      val thing = TileFeature(byteArrayTile: Tile, "meta")
      val ds = Seq(thing).toDS()
      write(ds)
      assert(ds.toDF.as[TileFeature[Tile, String]].collect().head === thing)
    }

    it("should code RDD[ProjectedRasterTile]") {
      val tile = TestData.projectedRasterTile(20, 30, -1.2, extent)
      val ds = Seq(tile).toDS()
      write(ds)
      assert(ds.toDF.as[ProjectedRasterTile].collect().head === tile)
    }

    it("should code RDD[Extent]") {
      val ds = Seq(extent).toDS()
      write(ds)
      assert(ds.toDF.as[Extent].collect().head === extent)
    }

    it("should code RDD[ProjectedExtent]") {
      val ds = Seq(pe).toDS()
      write(ds)
      assert(ds.toDF.as[ProjectedExtent].collect().head === pe)
    }

    it("should code RDD[TemporalProjectedExtent]") {
      val ds = Seq(tpe).toDS()
      write(ds)
      assert(ds.toDF.as[TemporalProjectedExtent].collect().head === tpe)
    }

    it("should code RDD[CellType]") {
      val ct = CellType.fromName("uint8")
      val ds = Seq(ct).toDS()
      write(ds)
      assert(ds.toDF.as[CellType].first() === ct)
    }

    it("should code RDD[TileLayerMetadata[SpaceTimeKey]]") {
      val ds = Seq(tlm).toDS()
      //ds.printSchema()
      //ds.show(false)
      write(ds)
      assert(ds.toDF.as[TileLayerMetadata[SpaceTimeKey]].first() === tlm)
    }

    it("should code RDD[SpatialKey]") {
      val ds = Seq((sk, stk)).toDS

      assert(ds.toDF.as[(SpatialKey, SpaceTimeKey)].first === (sk, stk))

      // This stinks: vvvvvvvv Encoders don't seem to work with UDFs.
      val key2col = udf((row: Row) => row.getInt(0))

      val colNum = ds.select(key2col(ds(ds.columns.head))).as[Int].first()
      assert(colNum === 37)
    }

    it("should code RDD[CRS]") {
      val values = Seq[CRS](LatLng, WebMercator, ConusAlbers, Sinusoidal)
      val ds = values.toDS()
      write(ds)

      val results = ds.toDF.as[CRS].collect()

      results should contain allElementsOf values
    }

    it("should code RDD[URI]") {
      val ds = Seq[URI](new URI("http://astraea.earth/"), new File("/tmp/humbug").toURI).toDS()
      write(ds)
      assert(ds.filter(u => Option(u.getHost).exists(_.contains("astraea"))).count === 1)
    }

    it("should code RDD[Envelope]") {
      val env = new Envelope(1, 2, 3, 4)
      val ds = Seq[Envelope](env).toDS()
      write(ds)
      assert(ds.first === env)
    }
  }
  describe("Dataframe encoding ops on spatial types") {

    it("should code RDD[Point]") {
      val points = Seq(null, extent.center.jtsGeom, null)
      val ds = points.toDS
      write(ds)
      assert(ds.collect().toSeq === points)
    }
  }
}
