/*
 * Copyright 2011 T8 Webware
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.banno.salat.avro

import com.novus.salat._
import scala.collection.mutable.{ HashMap, ListBuffer }
import org.apache.avro.io.{ Decoder, DatumReader }
import org.apache.avro.generic.{ GenericData, GenericDatumReader }
import org.apache.avro.util.Utf8

trait AvroDatumReader[X] extends DatumReader[X] {
  def read(decoder: Decoder): X
}

class AvroGenericDatumReader[X <: CaseClass](rootGrater: AvroGrater[X])(implicit ctx: Context)
  extends GenericDatumReader[X](rootGrater.asAvroSchema) with AvroDatumReader[X] {

  def read(decoder: Decoder): X = {
    val collectingGenericData = new CollectingGenericData
    val colletingReader = new GenericDatumReader[Object](rootGrater.asAvroSchema, rootGrater.asAvroSchema, collectingGenericData)

    colletingReader.read(null, decoder)

    val rootRecord = collectingGenericData.rootRecord.get
    val recordFields = collectingGenericData.fields
    val rootValues: ListBuffer[Object] = recordFields.get(rootRecord).get

    applyValues(rootRecord, rootValues, recordFields).asInstanceOf[X]
  }

  def applyValues(genericRecord: GenericData.Record, values: ListBuffer[Object], index: HashMap[GenericData.Record, ListBuffer[Object]]): Object = {
    // println("-------- apply values -------")
    // println("record = " + genericRecord)
    // println("values = " + values)

    val grater: AvroGrater[_] = ctx.lookup(genericRecord.getSchema.getFullName).get.asInstanceOf[AvroGrater[_]]

    val arguments = grater._indexedFields.zip(values).map {
      case (field, Some(record: GenericData.Record)) => Some(applyValues(record, index.get(record).get, index))
      case (field, Some(value)) =>
        val inTransformer = Injectors.select(field.typeRefType).getOrElse(field.in)
        inTransformer.transform_!(value)
      case (field, _) => grater.safeDefault(field)
    }.map(_.get.asInstanceOf[AnyRef])

    grater._constructor.newInstance(arguments: _*).asInstanceOf[AnyRef]
  }
    
  protected class CollectingGenericData extends GenericData {
    var rootRecord: Option[GenericData.Record] = None
    val fields = new HashMap[GenericData.Record, ListBuffer[Object]]
    override def setField(record: Any, name: String, pos: Int, obj: Object) {
      val genericRecord = record.asInstanceOf[GenericData.Record]
      rootRecord = Some(rootRecord.getOrElse(genericRecord))
      // println("------- set field --------")
      // println("record = " + record)
      // println("name = " + name)
      // println("pos = " + pos)
      // println("obj = " + obj)
      // println("fields = " + fields)
      val scalaObj = obj match {
        case utf8: Utf8 => utf8.toString
        case x => x
      }
      val recordFields = fields.getOrElse(genericRecord, new ListBuffer[Object])
      recordFields.insert(pos, Option(scalaObj))
      fields.update(genericRecord, recordFields)
    }
  }
}