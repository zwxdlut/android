////////////////////////////////////////////////////////////////////////////////
///
/// @file            customInputAudioAdapter.java
/// @authors
/// @date
///
/// @brief           Implementation of the customInputAudioAdapter class.
///
////////////////////////////////////////////////////////////////////////////////

package com.wuw_sample_engine.custom_audio;

import android.media.MediaRecorder;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.LinkedList;

import nuance.audio.IAudioInputAdapter;
import nuance.audio.IAudioInputAdapterListener;
import nuance.common.LogZone;
import nuance.common.ResultCode;

public class customInputAudioAdapter extends IAudioInputAdapter
{
  static LinkedList<customInputAudioAdapter> _sInstances = new LinkedList<customInputAudioAdapter>();

  IAudioInputAdapterListener _listener;
  audioIn _audioIn;
  CustomLogger logger;
  private audioIn.IAudioDataCallback audioDataCallback = null;

  public void setAudioDataCallback(audioIn.IAudioDataCallback callback) {
    audioDataCallback = callback;

    if (null != _audioIn) {
      _audioIn.setAudioDataCallback(audioDataCallback);
    }
  }

  //-----------------------------------------------------------------------------------------------------------------------------
  // Constructor
  //-----------------------------------------------------------------------------------------------------------------------------
  customInputAudioAdapter() throws ResultCode
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
    _sInstances.add(this);
  }



  //------------------------------------------------------------------------------
  // Base class:     IAudioInputAdapter
  // Caller:         AudioInput.
  // Description:    Configures the audio input adapter.
  // Parameters:     1) listener       Listener of this audio input adapter object.
  //                 2) adapterParams    Adapter specific configuration parameter json string.
  //                 3) interleavedFormat  Flag indicating whether audio data shall be in interleaved or non-interleaved format.
  //                 4) channelCount     Channel count.
  //                 5) sampleRate     Sample rate in hertz.
  //                 6) samplesPerChannel  Number of samples per channel.
  // Throws:         nuance.common.ResultCode
  //------------------------------------------------------------------------------
  @Override
  public void configure(IAudioInputAdapterListener listener, String adapterParams, boolean interleavedFormat, int channelCount, int sampleRate, int samplesPerChannel) throws ResultCode
  {
    logger.logMessage(LogZone.LOG_EXTERNAL_FUNC,"Entering function customInputAudioAdapter::configure");
    ResultCode rc = ResultCode.OK;
    try
    {
      _audioIn = new audioIn();
    }
    catch (ResultCode resultCode)
    {
      throw resultCode;
    }
    // set configuration
    if(null == _audioIn)
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid object for audioIn");
      rc = ResultCode.ERROR;
    }
    else if(null ==  listener)
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid object nuance_audio_IAudioInputAdapterListener");
      _audioIn.errorCode = "Invalid_Nuance_Audio_IAudioInputAdapterListener";
      rc = ResultCode.ERROR;
    }
    else if(0 >= channelCount)
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid value for channelCount");
      _audioIn.errorCode = "Invalid_ChannelCount";
      rc = ResultCode.ERROR;
    }
    else if(0 >= sampleRate)
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid value for sampleRate");
      _audioIn.errorCode = "Invalid_SampleRate";
      rc = ResultCode.ERROR;
    }
    else if(0 >= samplesPerChannel)
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid value for samplesPerChannel");
      _audioIn.errorCode = "Invalid_SamplesPerChannel";
      rc = ResultCode.ERROR;
    }
    else if(_audioIn.audio_state != audioIn.state.STATE_CLOSED)
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid state");
      _audioIn.errorCode = "Invalid_State";
      rc = ResultCode.ERROR;
    }
    else
    {
      //parsing adapter params to get audio source
      if(null != adapterParams)
      {
        try
        {
          JSONObject jsonObject = (JSONObject) new JSONTokener(adapterParams).nextValue();
          String source = (String) jsonObject.get("audio_source");

          switch (source.toUpperCase())
          {
            case "DEFAULT":
              _audioIn.audio_source = MediaRecorder.AudioSource.DEFAULT;
              break;
            case "MIC":
              _audioIn.audio_source = MediaRecorder.AudioSource.MIC;
              System.out.print("using mic");
              break;
            case "VOICE_UPLINK":
              _audioIn.audio_source = MediaRecorder.AudioSource.VOICE_UPLINK;
              break;
            case "VOICE_DOWNLINK":
              _audioIn.audio_source = MediaRecorder.AudioSource.VOICE_DOWNLINK;
              break;
            case "VOICE_CALL":
              _audioIn.audio_source = MediaRecorder.AudioSource.VOICE_CALL;
              break;
            case "CAMCORDER":
              _audioIn.audio_source = MediaRecorder.AudioSource.CAMCORDER;
              break;
            case "VOICE_RECOGNITION":
              _audioIn.audio_source = MediaRecorder.AudioSource.VOICE_RECOGNITION;
              break;
            case "VOICE_COMMUNICATION":
              _audioIn.audio_source = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
              break;
            default:
              logger.logMessage(LogZone.LOG_ERROR,"Invalid AudioSource");
              rc = ResultCode.ERROR;
              break;
          }
        }

        catch (Exception e)
        {
          logger.logMessage(LogZone.LOG_INFO,"No audio_source provided in JSON. Using default -> mic");
          _audioIn.audio_source = MediaRecorder.AudioSource.MIC;
        }
      }
      else
      {
        logger.logMessage(LogZone.LOG_INFO,"No audio_source provided in JSON. Using default -> mic");
        _audioIn.audio_source = MediaRecorder.AudioSource.MIC;
      }
      _listener = listener;
      _audioIn.channelCount = channelCount;
      _audioIn.sampleRate = sampleRate;
      _audioIn.samplesPerChannel = samplesPerChannel;
      _audioIn.setAudioDataCallback(audioDataCallback);
    }
    throw rc;
  }

  //----------------------------------------------------------------------------------------------
  // Base class:     IAudioInputAdapter
  // Caller:         AudioInput.
  // Description:    opens the audio data source represented by the audio input adapter with
  //                 the configuration that was given to the last call of the configure()
  //---------------------------------------------------------------------------------------------
  @Override
  public void open() throws ResultCode
  {
    logger.logMessage(LogZone.LOG_EXTERNAL_FUNC,"Entering function customInputAudioAdapter::open");
    ResultCode rc = ResultCode.OK;
    if(null != _audioIn)
    {
      if(_audioIn.audio_state != audioIn.state.STATE_CLOSED)
      {
        logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid state");
        _audioIn.errorCode = "Invalid_State";
        rc = ResultCode.ERROR;
      }
      else
      {
        // open customadapter audio input device
        String error = _audioIn.open(_listener);
        logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Returning from function audioIn::open");
        if(error != "NO_ERROR")
        {
          rc = ResultCode.ERROR;
          logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Function audioIn::open failed with error "+error);
        }
        else
        {
          // set new state (stopped, but opened)
          _audioIn.audio_state = audioIn.state.STATE_STOPPED;
        }
      }
    }
    else
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid object for audioIn");
      rc = ResultCode.ERROR;
    }
    throw rc;
  }

  //------------------------------------------------------------------------------
  // Base class:     IAudioInputAdapter
  // Caller:         AudioInput.
  // Description:    Starts capturing via the audio input adapter.
  //------------------------------------------------------------------------------
  @Override
  public void start() throws ResultCode
  {
    logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Entering function customInputAudioAdapter::start");
    ResultCode rc = ResultCode.OK;
    if(null != _audioIn)
    {
      if(_audioIn.audio_state != audioIn.state.STATE_STOPPED)
      {
        logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid state");
        _audioIn.errorCode = "Invalid_State";
        rc = ResultCode.ERROR;
      }
      else
      {
        // set state (started)
        _audioIn.audio_state = audioIn.state.STATE_STARTED;
        String error = _audioIn.start();
        logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Returning from function audioIn::start");
        // error cleanup
        if(error != "NO_ERROR")
        {
          rc = ResultCode.ERROR;
          logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Function audioIn::start failed with error "+error);
          // restore state
          _audioIn.audio_state = audioIn.state.STATE_STOPPED;
        }
      }
    }
    else
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid object for audioIn");
      rc = ResultCode.ERROR;
    }

    throw rc;
  }

  //------------------------------------------------------------------------------
  // Base class:     IAudioInputAdapter
  // Caller:         AudioInput.
  // Description:    Resumes capturing via the audio input adapter.
  //------------------------------------------------------------------------------
  @Override
  public void resume() throws ResultCode
  {
    logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Entering function customInputAudioAdapter::resume");
    ResultCode rc = ResultCode.OK;
    if(_audioIn.audio_state != audioIn.state.STATE_STARTED)
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid state");
      _audioIn.errorCode = "Invalid_State";
      rc = ResultCode.ERROR;
    }
    throw rc;
  }

  //------------------------------------------------------------------------------
  // Base class:     IAudioInputAdapter
  // Caller:         AudioInput.
  // Description:    Stops capturing via the audio input adapter.
  //------------------------------------------------------------------------------
  @Override
  public void stop() throws ResultCode
  {
    logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Entering function customInputAudioAdapter::stop");
    ResultCode rc = ResultCode.OK;
    if(null != _audioIn)
    {
      if(_audioIn.audio_state != audioIn.state.STATE_STARTED)
      {
        logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid state");
        _audioIn.errorCode = "Invalid_State";
        rc = ResultCode.ERROR;
      }
      else
      {
        // set state stopped
        _audioIn.audio_state = audioIn.state.STATE_STOPPED;
        // stop customadapter audio input
        String error = _audioIn.stop();
        logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Returning from function audioIn::stop");
        if(error != "NO_ERROR")
        {
          rc = ResultCode.ERROR;
          logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Function audioIn::stop failed with error "+error);
        }
      }
    }
    else
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid object for audioIn");
      rc = ResultCode.ERROR;
    }
    throw rc;
  }

  //------------------------------------------------------------------------------
  // Base class:     IAudioInputAdapter
  // Caller:         AudioInput.
  // Description:    Closes the audio input adapter.
  //------------------------------------------------------------------------------
  @Override
  public void close() throws ResultCode
  {
    logger.logMessage(LogZone.LOG_EXTERNAL_FUNC,"Entering function customInputAudioAdapter::close");
    ResultCode rc = ResultCode.OK;
    if(null != _audioIn)
    {
      if(_audioIn.audio_state != audioIn.state.STATE_STOPPED)
      {
        logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid state");
        _audioIn.errorCode = "Invalid_State";
        rc = ResultCode.ERROR;
      }
      else
      {
        // set state closed
        _audioIn.audio_state = audioIn.state.STATE_CLOSED;
        String error = _audioIn.close();
        logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Returning from function audioIn::close");
        if(error != "NO_ERROR")
        {
          rc = ResultCode.ERROR;
          logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Function audioIn::close failed with error "+error);
        }
      }
    }
    else
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid object for audioIn");
      rc = ResultCode.ERROR;
    }
    throw rc;
  }

  //-----------------------------------------------------------------------------------------
  // Base class:     IAudioInputAdapter
  // Caller:         AudioInput.
  // Description:    Returns a textual description of the last audio input adapter error.
  //-----------------------------------------------------------------------------------------
  @Override
  public String getErrorText()
  {
    logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Entering function customInputAudioAdapter::getErrorText");
    String audio_error;
    if(null != _audioIn)
    {
      audio_error = _audioIn.getErrorText();
      logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Returning from function audioIn::getErrorText");

    }
    else
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid object for audioIn");
      audio_error = "Invalid_AudioIn_Object";
    }
    return audio_error;
  }

  //------------------------------------------------------------------------------------------
  // Base class:     IAudioInputAdapter
  // Caller:         AudioInput.
  // Description:    Destroys this audio input adapter instance.
  //------------------------------------------------------------------------------------------
  @Override
  public void destroyAdapter()
  {
    logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Entering function customInputAudioAdapter::destroyAdapter");
    if(null != _audioIn)
    {
      _audioIn.releaseRecorder();
      logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Returning from function audioIn::destroyAdapter");
    }
    else
    {
      logger.logMessage(nuance.common.LogZone.LOG_ERROR,"Invalid object for audioIn");
    }
    logger.logModule.destroy();
    _sInstances.remove(this);
  }
}
