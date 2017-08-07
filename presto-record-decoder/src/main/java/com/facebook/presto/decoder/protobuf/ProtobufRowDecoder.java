/*
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
package com.facebook.presto.decoder.protobuf;

import com.facebook.presto.decoder.DecoderColumnHandle;
import com.facebook.presto.decoder.FieldDecoder;
import com.facebook.presto.decoder.FieldValueProvider;
import com.facebook.presto.decoder.RowDecoder;
import com.google.common.base.Splitter;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decoder for protobuf object.
 */
public class ProtobufRowDecoder
        implements RowDecoder
{
    public static final String NAME = "protobuf";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public boolean decodeRow(byte[] data,
            String dataSchema,
            Map<String, String> dataMap,
            Set<FieldValueProvider> fieldValueProviders,
            List<DecoderColumnHandle> columnHandles,
            Map<DecoderColumnHandle, FieldDecoder<?>> fieldDecoders)
    {
        Message protoRecord;
        Method method;

        try {
            String className = dataSchema;
            Class<?> clazz = Class.forName(className);
            method = clazz.getDeclaredMethod("parseFrom", byte[].class);

            protoRecord = (Message) method.invoke(null, data);
        }
        catch (Exception e) {
            return true;
        }

        for (DecoderColumnHandle columnHandle : columnHandles) {
            if (columnHandle.isInternal()) {
                continue;
            }

            @SuppressWarnings("unchecked")
            FieldDecoder<Object> decoder = (FieldDecoder<Object>) fieldDecoders.get(columnHandle);

            if (decoder != null) {
                Object element = locateElement(protoRecord, columnHandle);
                fieldValueProviders.add(decoder.decode(element, columnHandle));
            }
        }

        return false;
    }

    private Object locateElement(Message element, DecoderColumnHandle columnHandle)
    {
        Object value = element;
        for (String pathElement : Splitter.on('/').omitEmptyStrings().split(columnHandle.getMapping())) {
            Descriptors.FieldDescriptor fieldDescriptor = ((Message) value).getDescriptorForType()
                                                            .findFieldByName(pathElement);
            Message gm = (Message) value;
            value = null;
            // if field does not exist, just return null
            if (fieldDescriptor != null && fieldDescriptor.isRepeated() || gm.hasField(fieldDescriptor)) {
                value = gm.getField(fieldDescriptor);
            }
            if (value == null) {
                return null;
            }
        }
        return value;
    }
}