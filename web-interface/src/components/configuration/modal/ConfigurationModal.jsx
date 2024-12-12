import React, { useCallback, useEffect, useState } from 'react'
import DefaultValue from './DefaultValue'
import RestartRequired from './RestartRequired'
import ConfigurationInputField from './ConfigurationInputField'
import ConfigurationSubmitButton from './ConfigurationSubmitButton'
import ConfigurationCloseButton from './ConfigurationCloseButton'
import ConfigurationUpdateFailedWarning from './ConfigurationUpdateFailedWarning'
import ThreatLevelMidnight from './ThreatLevelMidnight'
import InputLabel from './InputLabel'

function ConfigurationModal (props) {
  const [inputDisabled, setInputDisabled] = useState(false)
  const [formDisabled, setFormDisabled] = useState(true)
  const [formSubmitting, setFormSubmitting] = useState(false)
  const [formSubmittedSuccessfully, setFormSubmittedSuccessfully] = useState(false)
  const [formSubmittedWithError, setFormSubmittedWithError] = useState(false)
  const [changeWarningAck, setChangeWarningAck] = useState(false)

  const [inputValue, setInputValue] = useState(props.config.value)

  const config = props.config

  const key = props.config.key
  const value = props.config.value
  const changeWarning = props.changeWarning
  const dbUpdateCallback = props.dbUpdateCallback
  const setLocalRevision = props.setLocalRevision

  // Optional: Used for dbUpdateCallback if it requires tenant scope.
  const organizationId = props.organizationId;
  const tenantId = props.tenantId;

  // Optional.
  const disabled = props.disabled;
  const disabledTitle = props.disabledTitle;

  useEffect(() => {
    if (changeWarning && !changeWarningAck) {
      setFormDisabled(true)
      return
    }

    if (inputValue === undefined || inputValue === value) {
      setFormDisabled(true)
    } else {
      if (config.constraints && config.constraints.length > 0) {
        for (const constraint of config.constraints) {
          const cData = constraint.data

          switch (constraint.type) {
            case "STRING_LENGTH":
              setFormDisabled(inputValue.length < cData.min || inputValue.length > cData.max)
              break
            case "NUMBER_RANGE":
              /* eslint-disable no-case-declarations */
              const numValue = parseInt(inputValue, 10)
              setFormDisabled(isNaN(numValue) || numValue < cData.min || numValue > cData.max)
              break
            case "SIMPLE_BOOLEAN":
              setFormDisabled(!(inputValue === true || inputValue === false))
              break
            case "ENUM_STRINGS":
              setFormDisabled(!cData.strings.includes(inputValue))
              break
            case "URI":
              let uriParsed;
              try {
                new URL(inputValue);
                uriParsed = true;
              } catch (_) {
                uriParsed = false;
              }

              setFormDisabled(!uriParsed);
              break;
            default:
              setFormDisabled(true)
          }
        }
      } else {
        setFormDisabled(false)
      }
    }
  }, [inputValue, value, changeWarningAck, changeWarning, config])

  const updateValue = useCallback(() => {
    setFormSubmittedWithError(false)
    setFormSubmitting(true)
    setFormDisabled(true)
    setInputDisabled(true)

    if (!organizationId && !tenantId) {
      dbUpdateCallback({
        [key]: inputValue
      }, onDbUpdateCallbackSuccess, onDbUpdateCallbackFailure)
    } else {
      if (organizationId && !tenantId) {
        dbUpdateCallback({
          [key]: inputValue
        }, organizationId, onDbUpdateCallbackSuccess, onDbUpdateCallbackFailure)
      } else {
        dbUpdateCallback({
          [key]: inputValue
        }, organizationId, tenantId, onDbUpdateCallbackSuccess, onDbUpdateCallbackFailure)
      }
    }
  }, [inputValue, key, dbUpdateCallback])

  const onDbUpdateCallbackSuccess = () => {
    setFormSubmitting(false)
    setFormDisabled(false)
    setFormSubmittedSuccessfully(true)
  }

  const onDbUpdateCallbackFailure = () => {
    setFormSubmittedWithError(true)
    setFormSubmitting(false)
    setFormDisabled(false)
    setInputDisabled(false)
  }

  const resetOnCancel = useCallback(() => {
    setInputValue(value)
    setChangeWarningAck(false)
    setFormSubmittedWithError(false)
  }, [value])

  const resetOnFinish = useCallback(() => {
    setChangeWarningAck(false)
    setFormSubmittedSuccessfully(false)
    setInputDisabled(false)
    setLocalRevision(prevRev => prevRev + 1)
  }, [setLocalRevision])

  if (disabled) {
    return <span className="text-muted" title={disabledTitle ? disabledTitle : "Value cannot be edited."}>Edit</span>
  }

  return (
        <React.Fragment>
            <a href="#"
               data-bs-toggle="modal"
               data-bs-target={'#configuration-dialog-' + key}>
                Edit
            </a>

            <div className="modal configuration-dialog" id={'configuration-dialog-' + key}
                 data-bs-keyboard="false" data-bs-backdrop="static" tabIndex="-1">
                <div className="modal-dialog">
                    <div className="modal-content">
                        <div className="modal-header">
                            <h5 className="modal-title">Edit Configuration Value</h5>
                        </div>

                        <div className="modal-body">
                            <InputLabel config={props.config} />

                            <ConfigurationInputField
                                type={props.config.value_type}
                                title={props.config.key_human_readable}
                                constraints={props.config.constraints}
                                fieldKey={key}
                                value={inputValue}
                                setValue={setInputValue}
                                disabled={inputDisabled} />

                            <div className="form-text">
                                <DefaultValue value={props.config.default_value} />
                            </div>

                            <RestartRequired required={props.config.requires_restart} />
                            <ConfigurationUpdateFailedWarning failed={formSubmittedWithError} />
                            <ThreatLevelMidnight
                                enabled={props.changeWarning}
                                helpTag={props.config.help_tag}
                                configKey={key}
                                changeWarningAck={changeWarningAck}
                                setChangeWarningAck={setChangeWarningAck} />
                        </div>

                        <div className="modal-footer">
                            <ConfigurationCloseButton
                                submitting={formSubmitting}
                                submittedSuccessfully={formSubmittedSuccessfully}
                                onClick={resetOnCancel} />

                            <ConfigurationSubmitButton
                                onClick={updateValue}
                                disabled={formDisabled}
                                submitting={formSubmitting}
                                submittedSuccessfully={formSubmittedSuccessfully}
                                onFinishedClick={resetOnFinish} />
                        </div>
                    </div>
                </div>
            </div>
        </React.Fragment>
  )
}

export default ConfigurationModal
