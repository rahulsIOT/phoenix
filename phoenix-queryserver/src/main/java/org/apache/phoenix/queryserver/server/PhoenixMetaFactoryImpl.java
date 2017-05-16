/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.queryserver.server;

import com.google.common.base.Preconditions;
import org.apache.calcite.avatica.Meta;
import org.apache.calcite.avatica.jdbc.JdbcMeta;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.phoenix.jdbc.PhoenixConnection;
import org.apache.phoenix.mapreduce.util.PhoenixConfigurationUtil;
import org.apache.phoenix.query.QueryServices;
import org.apache.phoenix.queryserver.metrics.PqsMetricsSystem;
import org.apache.phoenix.util.QueryUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.apache.phoenix.query.QueryServices.PHOENIX_QUERY_SERVER_METRICS;
import static org.apache.phoenix.query.QueryServicesOptions.DEFAULT_PHOENIX_QUERY_SERVER_METRICS;

/**
 * Bridge between Phoenix and Avatica.
 */
public class PhoenixMetaFactoryImpl extends Configured implements PhoenixMetaFactory {

  // invoked via reflection
  public PhoenixMetaFactoryImpl() {
    super(HBaseConfiguration.create());
  }

  // invoked via reflection
  public PhoenixMetaFactoryImpl(Configuration conf) {
    super(conf);
  }

  @Override
  public Meta create(List<String> args) {
    Configuration conf = Preconditions.checkNotNull(getConf(), "Configuration must not be null.");
    Properties info = new Properties();
    info.putAll(conf.getValByRegex("avatica.*"));
    try {
      final String url;
      if (args.size() == 0) {
        url = QueryUtil.getConnectionUrl(info, conf);
      } else if (args.size() == 1) {
        url = args.get(0);
      } else {
        throw new RuntimeException(
            "0 or 1 argument expected. Received " + Arrays.toString(args.toArray()));
      }
      // TODO: what about -D configs passed in from cli? How do they get pushed down?
      boolean isMetricOn = conf.getBoolean(PHOENIX_QUERY_SERVER_METRICS,
              DEFAULT_PHOENIX_QUERY_SERVER_METRICS);
      if (isMetricOn) {
        info.put("pqs_reporting_interval", PqsMetricsSystem.getReportingInterval(conf));
        info.put("pqs_filename",PqsMetricsSystem.getSinkFileName(conf));
        info.put("pqs_sinktype",PqsMetricsSystem.getTypeOfSink(conf));
        info.put(QueryServices.COLLECT_REQUEST_LEVEL_METRICS, "true");
        return new PQSMetricsMeta(url, info);
      } else {
        return new JdbcMeta(url, info);
      }

    } catch (SQLException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
