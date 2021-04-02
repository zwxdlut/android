////////////////////////////////////////////////////////////////////////////////
///
/// @file            customInputAdapterFactory.java
///
/// @brief           Implementation of the customInputAdapterFactory class.
///
////////////////////////////////////////////////////////////////////////////////

package com.wuw_sample_engine.custom_audio;

import nuance.audio.IAudioInputAdapterFactory;
import nuance.audio.IAudioInputAdapterFactoryListener;
import nuance.common.LogZone;
import nuance.common.ResultCode;

public class customInputAdapterFactory extends  IAudioInputAdapterFactory
{
  customInputAudioAdapter adapterInstance;
  CustomLogger logger ;
  audioIn.IAudioDataCallback audioDataCallback = null;

  public void setAudioDataCallback(audioIn.IAudioDataCallback callback) {
    audioDataCallback = callback;

    if (null != adapterInstance) {
      adapterInstance.setAudioDataCallback(audioDataCallback);
    }
  }

  //-----------------------------------------------------------------------------------------------------------------------------
  // Constructor
  //-----------------------------------------------------------------------------------------------------------------------------
  public customInputAdapterFactory() throws ResultCode
  {
    logger = new CustomLogger();
    try
    {
      logger.logModule = logger.logging.createLogModule("nuance.custom.AUDIO");
    }
    catch (ResultCode rc)
    {
      throw rc;
    }

  }

  //---------------------------------------------------------------------------------------------------------
  // Caller:         User application context, via the audio manager.
  // Description:    Returns the type identifier of the audio adapters created, for instance "CUSTOM_AUDIO"
  //---------------------------------------------------------------------------------------------------------
  @Override
  public String getAdapterType()
  {
    logger.logMessage(nuance.common.LogZone.LOG_EXTERNAL_FUNC, "Entering function customInputAdapterFactory::getAdapterType");
    String adapterType = "CUSTOM_AUDIO"; //Adapter type that needs to be specified in audioconfig
    return adapterType;
  }

  //-----------------------------------------------------------------------------------------------------------------------------
  // Caller:         User application context, via the audio manager.
  // Description:    Creates an audio Input adapter instance.
  // Parameter:      instanceHandleReceiver   listener object handle which receives the created adapter instance.
  //------------------------------------------------------------------------------------------------------------------------------
  @Override
  public void createAudioInputAdapter(IAudioInputAdapterFactoryListener instanceHandleReceiver) throws ResultCode
  {
    logger.logMessage(nuance.common.LogZone.LOG_EXTERNAL_FUNC, "Entering function customInputAdapterFactory::createAudioInputAdapter");
    ResultCode rc = ResultCode.OK;
    if(null == instanceHandleReceiver)
    {
      logger.logMessage(LogZone.LOG_ERROR, "Invalid object for nuance_audio_IAudioInputAdapterFactoryListener");
      rc = ResultCode.ERROR;
    }
    else
    {
      try
      {
        adapterInstance = new customInputAudioAdapter();
      }
      catch (ResultCode resultCode)
      {
        throw  resultCode;
      }
      if(null == adapterInstance)
      {
        logger.logMessage(LogZone.LOG_ERROR, "Constructor call for customInputAudioAdapter failed");
        rc = ResultCode.ERROR;
      }
      else
      {
        instanceHandleReceiver.onInputAdapterCreated(adapterInstance);
        adapterInstance.setAudioDataCallback(audioDataCallback);
      }
    }
    throw  rc;
  }

  //---------------------------------------------------------------------------------------------------------
  // Caller:         audio manager destructor.
  // Description:    Release this instance of the factory.
  //---------------------------------------------------------------------------------------------------------
  @Override
  public void releaseFactory()
  {
    logger.logMessage(nuance.common.LogZone.LOG_EXTERNAL_FUNC, "Entering function customInputAdapterFactory::releaseFactory");
    logger.logModule.destroy();
    customInputAudioAdapter._sInstances.clear();
  }

}
