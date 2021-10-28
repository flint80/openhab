import org.openhab.core.automation.Action
import org.openhab.core.automation.module.script.rulesupport.shared.ScriptedAutomationManager
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule
import org.openhab.core.config.core.Configuration
import org.openhab.core.items.ItemRegistry
import org.openhab.core.library.items.SwitchItem
import org.openhab.core.library.types.OnOffType
import org.openhab.core.library.types.QuantityType
import org.openhab.core.types.State
import org.slf4j.LoggerFactory

scriptExtension.importPreset("RuleSupport")
scriptExtension.importPreset("RuleSimple")

def logger = LoggerFactory.getLogger("scripts")

def am = automationManager as ScriptedAutomationManager

def co2ItemsIds = ["co2_cabinet"]
def refValuesIds = ["co2_ref_cabinet"]
def manualSwitch = ["man_cabinet"]
def relaysIds = ["rele_attic_vent"]
def delta = 100 as Double

def sRule = new SimpleRule() {
    @Override
    Object execute(Action action, Map<String, ?> map) {
        def _items = items as Map<String, State>
        def ir = itemRegistry as ItemRegistry
        try {
            co2ItemsIds.withIndex().forEach {
                def id = it.getV1()
                def index = it.getV2()
                def manualMode = _items[manualSwitch[index]] == OnOffType.ON
                if (manualMode) {
                    logger.debug("id = ${id}: manual mode is ON, returning")
                } else {
                    def co2 = (_items[id] as Number)?.doubleValue() ?: 500
                    def refCo2 = (_items[refValuesIds[index]] as Number)?.doubleValue() ?: 600
                    def relay = (_items[relaysIds[index]] as OnOffType) ?: OnOffType.ON
                    def newRelay = relay
                    def relayItem = ir.getItem(relaysIds[index]) as SwitchItem
                    if (relay == OnOffType.OFF && (co2 - refCo2 > delta)) {
                        newRelay = OnOffType.ON
                    } else if (relay == OnOffType.ON && (refCo2 - co2 > delta)) {
                        newRelay = OnOffType.OFF
                    }
                    logger.debug("id = ${id}: co2=${co2}, ref value = ${refCo2}, relay = ${relay.name()}, sending ${newRelay.name()}")
                    relayItem.send(newRelay)
                }
            }
        } catch (Exception e) {
            logger.error("unable to perform script ", e)
        }

    }
}

sRule.setTriggers([
        TriggerBuilder.create()
                .withId("aTermostatTrigger")
                .withTypeUID("timer.GenericCronTrigger")
                .withConfiguration(new Configuration([cronExpression: "30 * * * * ?"]))
                .build()
])

am.addRule(sRule)

logger.debug("co2 automation script is loaded")