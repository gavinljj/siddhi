package org.wso2.siddhi.core.util.parser;

import org.wso2.siddhi.core.config.SiddhiAppContext;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.state.MetaStateEvent;
import org.wso2.siddhi.core.event.state.StateEvent;
import org.wso2.siddhi.core.event.stream.MetaStreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.exception.SiddhiAppRuntimeException;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.executor.VariableExpressionExecutor;
import org.wso2.siddhi.core.query.input.stream.single.EntryValveExecutor;
import org.wso2.siddhi.core.query.input.stream.single.SingleStreamRuntime;
import org.wso2.siddhi.core.query.selector.attribute.aggregator.incremental.IncrementalExecutor;
import org.wso2.siddhi.core.table.Table;
import org.wso2.siddhi.core.util.collection.operator.CompiledCondition;
import org.wso2.siddhi.core.util.collection.operator.IncrementalAggregateCompileCondition;
import org.wso2.siddhi.core.util.collection.operator.MatchingMetaInfoHolder;
import org.wso2.siddhi.core.util.parser.helper.QueryParserHelper;
import org.wso2.siddhi.query.api.aggregation.TimePeriod;
import org.wso2.siddhi.query.api.aggregation.Within;
import org.wso2.siddhi.query.api.definition.AbstractDefinition;
import org.wso2.siddhi.query.api.definition.AggregationDefinition;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.definition.StreamDefinition;
import org.wso2.siddhi.query.api.expression.Expression;
import org.wso2.siddhi.query.api.expression.condition.Compare;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.wso2.siddhi.core.util.SiddhiConstants.UNKNOWN_STATE;

public class AggregationRuntime {
    private final AggregationDefinition aggregationDefinition;
    private final Map<TimePeriod.Duration, IncrementalExecutor> incrementalExecutorMap;
    private final Map<TimePeriod.Duration, Table> aggregationTables;
    private final SiddhiAppContext siddhiAppContext;
    private final MetaStreamEvent metaStreamEvent;
    private List<TimePeriod.Duration> incrementalDurations;
    private SingleStreamRuntime singleStreamRuntime;
    private EntryValveExecutor entryValveExecutor;
    private ExpressionExecutor perExpressionExecutor;
    private Map<TimePeriod.Duration, ComplexEventChunk<StreamEvent>> runningAggregationEventMap = new HashMap<>();
    private Map<TimePeriod.Duration, Object> inMemoryStoreMap = new HashMap<>();
    private List<ExpressionExecutor> baseExecutors;

    public AggregationRuntime(AggregationDefinition aggregationDefinition,
            Map<TimePeriod.Duration, IncrementalExecutor> incrementalExecutorMap,
            Map<TimePeriod.Duration, Table> aggregationTables, SingleStreamRuntime singleStreamRuntime,
            EntryValveExecutor entryValveExecutor, List<TimePeriod.Duration> incrementalDurations,
            SiddhiAppContext siddhiAppContext, List<ExpressionExecutor> baseExecutors,
            MetaStreamEvent metaStreamEvent) {
        this.aggregationDefinition = aggregationDefinition;
        this.incrementalExecutorMap = incrementalExecutorMap;
        this.aggregationTables = aggregationTables;
        this.incrementalDurations = incrementalDurations;
        this.siddhiAppContext = siddhiAppContext;
        this.singleStreamRuntime = singleStreamRuntime;
        this.entryValveExecutor = entryValveExecutor;
        this.baseExecutors = baseExecutors;
        this.metaStreamEvent = metaStreamEvent;
    }

    public Map<TimePeriod.Duration, IncrementalExecutor> getIncrementalExecutorMap() {
        return incrementalExecutorMap;
    }

    public Map<TimePeriod.Duration, Table> getAggregationTables() {
        return aggregationTables;
    }

    public AggregationDefinition getAggregationDefinition() {
        return aggregationDefinition;
    }

    public SiddhiAppContext getSiddhiAppContext() {
        return siddhiAppContext;
    }

    public SingleStreamRuntime getSingleStreamRuntime() {
        return singleStreamRuntime;
    }

    public EntryValveExecutor getEntryValveExecutor() {
        return entryValveExecutor;
    }

    public List<TimePeriod.Duration> getIncrementalDurations() {
        return incrementalDurations;
    }

    public StreamEvent find(StateEvent matchingEvent, CompiledCondition compiledCondition) {
        String perValueAsString = perExpressionExecutor.execute(matchingEvent).toString();
        TimePeriod.Duration perValue = TimePeriod.Duration.valueOf(perValueAsString.toUpperCase());
        if (!incrementalExecutorMap.keySet().contains(perValue)) {
            throw new SiddhiAppRuntimeException("The aggregate values for " + perValue.toString()
                    + " granularity cannot be provided since aggregation definition " + aggregationDefinition.getId()
                    + " does not contain " + perValue.toString() + " duration");
        }
        return ((IncrementalAggregateCompileCondition) compiledCondition).find(matchingEvent, perValue,
                incrementalExecutorMap.get(perValue).isRoot());
        // todo implement
    }

    public IncrementalAggregateCompileCondition compileCondition(Expression expression, Within within, Expression per,
            MatchingMetaInfoHolder matchingMetaInfoHolder, List<VariableExpressionExecutor> variableExpressionExecutors,
            Map<String, Table> tableMap, String queryName, SiddhiAppContext siddhiAppContext) {
        Map<TimePeriod.Duration, CompiledCondition> tableCompiledConditions = new HashMap<>();
        Map<TimePeriod.Duration, CompiledCondition> inMemoryStoreCompileConditions = new HashMap<>();
        perExpressionExecutor = ExpressionParser.parseExpression(per, matchingMetaInfoHolder.getMetaStateEvent(),
                matchingMetaInfoHolder.getCurrentState(), tableMap, variableExpressionExecutors, siddhiAppContext,
                false, 0, queryName);
        // todo implement
        if (within.getTimeRange().size() == 2) {
            // TODO: 7/21/17 We assume that within.getTimeRange() values are in "yyyy-MM-dd HH:mm:ss" format
            Expression greaterThanEqualExpression = Expression.compare(Expression.variable("_TIMESTAMP"),
                    Compare.Operator.GREATER_THAN_EQUAL, Expression.function("time", "timestampInMilliseconds",
                            within.getTimeRange().get(0), Expression.value("yyyy-MM-dd HH:mm:ss")));
            Expression lessThanExpression = Expression.compare(Expression.variable("_TIMESTAMP"),
                    Compare.Operator.LESS_THAN, Expression.function("time", "timestampInMilliseconds",
                            within.getTimeRange().get(1), Expression.value("yyyy-MM-dd HH:mm:ss")));
            /*Expression completeExpression = Expression.and(expression,
                    Expression.and(greaterThanEqualExpression, lessThanExpression));*/
            // TODO: 7/26/17 this doesnt work coz original exp is made for aggregation. not tables
            Expression completeExpression = Expression.and(greaterThanEqualExpression, lessThanExpression);
            Integer i = 0;
            for (Map.Entry<TimePeriod.Duration, Table> entry : aggregationTables.entrySet()) {
                CompiledCondition tableCompileCondition = entry.getValue().compileCondition(completeExpression,
                        newMatchingMetaInfoHolderForTables(matchingMetaInfoHolder,
                                entry.getValue().getTableDefinition()),
                        siddhiAppContext, variableExpressionExecutors, tableMap, queryName);
                tableCompiledConditions.put(entry.getKey(), tableCompileCondition);

                ComplexEventChunk<StreamEvent> complexEventChunk = new ComplexEventChunk<>(true);
                runningAggregationEventMap.put(entry.getKey(), complexEventChunk);
                CompiledCondition inMemoryStoreCompileCondition = OperatorParser.constructOperator(complexEventChunk,
                        completeExpression,
                        newMatchingMetaInfoHolderForComplexEventChunk(matchingMetaInfoHolder,
                                entry.getValue().getTableDefinition().getAttributeList(),
                                "_".concat((++i).toString() + "_StreamDefinition"), variableExpressionExecutors),
                        siddhiAppContext, variableExpressionExecutors, tableMap, queryName);
                inMemoryStoreCompileConditions.put(entry.getKey(), inMemoryStoreCompileCondition);
                inMemoryStoreMap.put(entry.getKey(), incrementalExecutorMap.get(entry.getKey()).getInMemoryStore());
            }
            return new IncrementalAggregateCompileCondition(tableCompiledConditions, inMemoryStoreCompileConditions,
                    baseExecutors, runningAggregationEventMap, aggregationTables, inMemoryStoreMap, metaStreamEvent);

        } else { // TODO: 7/24/17 implement for one within value
            throw new SiddhiAppRuntimeException("Only one or two values allowed for within condition");
        }
    }

    private MatchingMetaInfoHolder newMatchingMetaInfoHolderForTables(MatchingMetaInfoHolder matchingMetaInfoHolder,
            AbstractDefinition tableDefinition) {
        MetaStreamEvent rightMetaStreamEventForTable = new MetaStreamEvent();
        rightMetaStreamEventForTable.setEventType(MetaStreamEvent.EventType.TABLE);
        rightMetaStreamEventForTable.addInputDefinition(tableDefinition);
        // The input ref id of aggregator is set as ref if for tables. // TODO: 7/24/17 change when on condition is
        // retrieved from aggregate and not tables
        rightMetaStreamEventForTable.setInputReferenceId(
                matchingMetaInfoHolder.getMetaStateEvent().getMetaStreamEvent(1).getInputReferenceId());
        MetaStateEvent metaStateEvent = new MetaStateEvent(2);
        metaStateEvent.addEvent(matchingMetaInfoHolder.getMetaStateEvent().getMetaStreamEvent(0));
        metaStateEvent.addEvent(rightMetaStreamEventForTable);

        return MatcherParser.constructMatchingMetaStateHolder(metaStateEvent, 0, tableDefinition, UNKNOWN_STATE);
    }

    private MatchingMetaInfoHolder newMatchingMetaInfoHolderForComplexEventChunk(
            MatchingMetaInfoHolder matchingMetaInfoHolder, List<Attribute> attributeList, String streamDefinitionID,
            List<VariableExpressionExecutor> variableExpressionExecutors) {
        MetaStreamEvent rightMetaStreamEventForComplexEventChunk = new MetaStreamEvent();
        rightMetaStreamEventForComplexEventChunk.setEventType(MetaStreamEvent.EventType.WINDOW);
        // TODO: 7/26/17 is it ok to set type WINDOW? Otherwise, _TIMESTAMP variable not parsed
        StreamDefinition streamDefinition = StreamDefinition.id(streamDefinitionID);
        for (Attribute attribute : attributeList) {
            streamDefinition.attribute(attribute.getName(), attribute.getType());
        }
        rightMetaStreamEventForComplexEventChunk.addInputDefinition(streamDefinition);
        rightMetaStreamEventForComplexEventChunk.setOutputDefinition(streamDefinition);
        QueryParserHelper.reduceMetaComplexEvent(rightMetaStreamEventForComplexEventChunk);
        QueryParserHelper.updateVariablePosition(rightMetaStreamEventForComplexEventChunk, variableExpressionExecutors);
        // The input ref id of aggregator is set as ref if for new stream. // TODO: 7/24/17 change when on condition is
        // retrieved from aggregate and not new stream
        rightMetaStreamEventForComplexEventChunk.setInputReferenceId(
                matchingMetaInfoHolder.getMetaStateEvent().getMetaStreamEvent(1).getInputReferenceId());
        MetaStateEvent metaStateEvent = new MetaStateEvent(2);
        metaStateEvent.addEvent(matchingMetaInfoHolder.getMetaStateEvent().getMetaStreamEvent(0));
        metaStateEvent.addEvent(rightMetaStreamEventForComplexEventChunk);

        return MatcherParser.constructMatchingMetaStateHolder(metaStateEvent, 0, streamDefinition, UNKNOWN_STATE);
    }
}
