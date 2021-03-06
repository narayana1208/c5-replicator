/*
 * Copyright 2014 WANdisco
 *
 *  WANdisco licenses this file to you under the Apache License,
 *  version 2.0 (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */

package c5db.log;

import c5db.interfaces.replication.QuorumConfiguration;
import c5db.replication.ReplicatorTestUtil;
import c5db.replication.generated.LogEntry;
import com.google.common.collect.Lists;
import org.junit.Test;

import static c5db.interfaces.replication.QuorumConfiguration.EMPTY;
import static c5db.log.OLogEntryOracle.QuorumConfigurationWithSeqNum;
import static c5db.replication.ReplicatorTestUtil.entries;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class NavigableMapOLogEntryOracleTest {
  private final NavigableMapOLogEntryOracle oracle = new NavigableMapOLogEntryOracle();

  private final QuorumConfiguration firstConfig = QuorumConfiguration.of(Lists.newArrayList(1L));
  private final QuorumConfiguration secondConfig = firstConfig.getTransitionalConfiguration(Lists.newArrayList(2L));

  @Test
  public void returnsTheElectionTermAtAGivenSeqNum() throws Exception {
    havingLogged(
        entries()
            .term(17).seqNums(5, 6, 7)
            .term(18).seqNums(8, 9, 10));

    assertThat(oracle.getTermAtSeqNum(4), is(equalTo(0L)));
    assertThat(oracle.getTermAtSeqNum(5), is(equalTo(17L)));
    assertThat(oracle.getTermAtSeqNum(10), is(equalTo(18L)));
  }

  @Test
  public void handlesTruncationsAndUpdatesTermInformationAccordingly() throws Exception {
    havingLogged(
        entries()
            .term(7).seqNums(1, 2));
    havingTruncatedToSeqNum(2);
    havingLogged(
        entries()
            .term(8).seqNums(2));

    assertThat(oracle.getTermAtSeqNum(1), is(equalTo(7L)));
    assertThat(oracle.getTermAtSeqNum(2), is(equalTo(8L)));
  }

  @Test
  public void returnsTheLastQuorumConfigurationAndItsSeqNum() throws Exception {
    havingLogged(
        entries()
            .term(999)
            .seqNums(5)
            .configurationAndSeqNum(firstConfig, 6)
            .seqNums(7, 8, 9));
    assertThat(oracle.getLastQuorumConfig(), is(equalTo(configurationAndSeqNum(firstConfig, 6))));

    havingLogged(
        entries()
            .term(999)
            .configurationAndSeqNum(secondConfig, 10));

    assertThat(oracle.getLastQuorumConfig(), is(equalTo(configurationAndSeqNum(secondConfig, 10))));
  }

  @Test
  public void handlesTruncationsAndUpdatesQuorumConfigurationInformationAccordingly() throws Exception {
    havingLogged(
        entries()
            .term(7).configurationAndSeqNum(firstConfig, 1));
    havingTruncatedToSeqNum(1);
    assertThat(oracle.getLastQuorumConfig(), is(equalTo(configurationAndSeqNum(EMPTY, 0))));

    havingLogged(
        entries()
            .term(8).configurationAndSeqNum(secondConfig, 2));
    assertThat(oracle.getLastQuorumConfig(), is(equalTo(configurationAndSeqNum(secondConfig, 2))));
  }

  @Test
  public void reportsAGreatestSeqNumOfZeroWhenNothingHasBeenLogged() throws Exception {
    assertThat(oracle.getGreatestSeqNum(), is(equalTo(0L)));
  }

  @Test
  public void reportsTheGreatestSeqNumLogged() throws Exception {
    havingLogged(
        entries()
            .term(8).seqNums(1, 2, 3));
    assertThat(oracle.getGreatestSeqNum(), is(equalTo(3L)));
  }

  @Test
  public void takesTruncationIntoAccountWhenReportingTheGreatestSeqNumLogged() throws Exception {
    havingLogged(
        entries()
            .term(8).seqNums(1, 2, 3));

    havingTruncatedToSeqNum(2);
    assertThat(oracle.getGreatestSeqNum(), is(equalTo(1L)));

    havingTruncatedToSeqNum(1);
    assertThat(oracle.getGreatestSeqNum(), is(equalTo(0L)));
  }

  private void havingLogged(ReplicatorTestUtil.LogSequenceBuilder sequenceBuilder) {
    for (LogEntry entry : sequenceBuilder.build()) {
      OLogEntry oLogEntry = OLogEntry.fromProtostuff(entry);
      oracle.notifyLogging(oLogEntry);
    }
  }

  private void havingTruncatedToSeqNum(long seqNum) {
    oracle.notifyTruncation(seqNum);
  }

  private QuorumConfigurationWithSeqNum configurationAndSeqNum(QuorumConfiguration quorumConfiguration,
                                                               long seqNum) {
    return new QuorumConfigurationWithSeqNum(quorumConfiguration, seqNum);
  }
}
