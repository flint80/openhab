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

def temperatureItemsIds = ["t_cabinet", "t_bedroom"]
def thermostatsItemsIds = ["ts_cabinet", "ts_bedroom"]
def manualTemperatureSwitch = ["man_cabinet","man_bedroom"]
def valvesIds = ["valve_cabinet", "valve_bedroom"]
def summerModeSwitchId = "summer_mode"
def delta = 0.5 as Double

def sRule = new SimpleRule() {
    @Override
    Object execute(Action action, Map<String, ?> map) {
        def _items = items as Map<String, State>
        def ir = itemRegistry as ItemRegistry
        try {
            temperatureItemsIds.withIndex().forEach {
                def summerMode = _items[summerModeSwitchId] == OnOffType.ON
                def id = it.getV1()
                def index = it.getV2()
                def manualMode = _items[manualTemperatureSwitch[index]] == OnOffType.ON
                if (manualMode) {
                    logger.debug("id = ${id}: manual mode is ON, returning")
                } else {
                    def t = (_items[id] as Number)?.doubleValue() ?: 22
                    def ts = (_items[thermostatsItemsIds[index]] as Number)?.doubleValue() ?: 22
                    def valve = (_items[valvesIds[index]] as OnOffType) ?: OnOffType.ON
                    def newValve = valve
                    def valveItem = ir.getItem(valvesIds[index]) as SwitchItem
                    if (summerMode) {
                        newValve = OnOffType.ON
                    } else {
                        if (valve == OnOffType.ON && (t - ts > delta)) {
                            newValve = OnOffType.OFF
                        } else if (valve == OnOffType.OFF && (ts - t > delta)) {
                            newValve = OnOffType.ON
                        }
                    }
                    logger.debug("id = ${id}: temperature=${t}, thermostat = ${ts}, valve = ${valve.name()}, summer mode = ${summerMode}, sending ${newValve.name()}")
                    valveItem.send(newValve)
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
                .withConfiguration(new Configuration([cronExpression: "0 * * * * ?"]))
                .build()
])

am.addRule(sRule)

logger.debug("thermostat automation script is loaded")