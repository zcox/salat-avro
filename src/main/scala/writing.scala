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
import org.apache.avro.Schema
import org.apache.avro.generic.{ GenericData, GenericDatumWriter }
import org.apache.avro.util.Utf8

class AvroGenericDatumWriter[X <: CaseClass](grater: AvroGrater[X])(implicit ctx: Context)
  extends GenericDatumWriter[X](grater.asAvroSchema, new AvroProductGenericData)

class AvroProductGenericData(implicit ctx: Context) extends GenericData {
  override def getField(record: Any, name: String, pos: Int): Object = {
    // println("getField in grater %s \n\twith record %s\n\twith name %s \n\tat pos %s".format(AvroGrater.this, record, name, pos))
    val caseClass = record.asInstanceOf[Product]
    val grater: AvroGrater[_] = ctx.lookup(caseClass.getClass.getName).get.asInstanceOf[AvroGrater[_]]

    val (value, field) = caseClass.productIterator.zip(grater._indexedFields.iterator).toList.apply(pos)
    val outTransformer = Extractors.select(field.typeRefType).getOrElse(field.out)
    // println("out transformer is " + outTransformer)
    val returnedValue = outTransformer.transform_!(value) match {
      case Some(None) => None
      case Some(serialized) => serialized.asInstanceOf[AnyRef]
      case _ => None
    }
    // println("returnedValue = " + returnedValue)
    returnedValue
  }

  override def resolveUnion(union: Schema, datum: Any): Int = datum match {
    case None => 1
    case _ => 0
  }
}