////////////////////////////////////////////////////////////////////////////////
///
/// @file            audioIn.java
/// @authors
/// @date
///
/// @brief           Implementation of the audioIn class.
///
/// Android AudioRecord interface reference documentation:
/// https://developer.android.com/reference/android/media/AudioRecord.html
////////////////////////////////////////////////////////////////////////////////

package com.wuw_sample_engine.custom_audio;

import nuance.audio.IAudioInputAdapterListener;
import nuance.common.ResultCode;
import nuance.common.LogZone;

import android.media.AudioFormat;
import android.media.AudioRecord;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class audioIn
{
  private CustomLogger logger;
  private final int CHANNEL_CONFIG    = AudioFormat.CHANNEL_IN_MONO;
  private final int AUDIO_FORMAT      = AudioFormat.ENCODING_PCM_16BIT;
  private AudioRecord recorder;
  private int bufferSizeInBytes;
  private IAudioInputAdapterListener listener;
  private Thread record;
  public int channelCount;
  public int sampleRate;
  public int samplesPerChannel;
  public int bufferCount;
  public int audio_source;
  public String errorCode = "NO_ERROR";
  private IAudioDataCallback audioDataCallback = null;

  /*------------------------------------------------------------------------
   * @brief    audio State enumeration.
   *------------------------------------------------------------------------*/
  public enum state
  {
    STATE_CLOSED ,    // audio input adapter is closed
    STATE_STOPPED,    // audio input adapter is stopped, but opened
    STATE_STARTED     // audio input adapter is started
  };

  // set state (closed)
  state audio_state = state.STATE_CLOSED;

  public interface IAudioDataCallback {
    public void onCapture(byte buf[], int size);
  }

  public void setAudioDataCallback(IAudioDataCallback callback) {
    audioDataCallback = callback;
  }

  //-----------------------------------------------------------------------------------------------------------------------------
  // Constructor
  //-----------------------------------------------------------------------------------------------------------------------------
  audioIn() throws ResultCode
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

  //-----------------------------------------------------------------------------------------------------------------------------
  // Caller:         InputAudioAdapter.
  // Description:    This method opens the audio data sink (e.g. audio device) represented by the audio input adapter with the
  //                 configuration that was given to the last call of the customInputAudioAdapter::configure() method.
  // Returns:        Error in text form.
  //-----------------------------------------------------------------------------------------------------------------------------
  public String open(IAudioInputAdapterListener _listener)
  {
    logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Entering function audioIn::open");
    listener = _listener;
    bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRate,CHANNEL_CONFIG,AUDIO_FORMAT);
    bufferSizeInBytes = bufferSizeInBytes*2;//Taking a higher value of buffer to guarantee a smooth recording
    try
    {
      recorder = new AudioRecord(audio_source, sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSizeInBytes * 2);//Since short=2bytes,bufferSizeInBytes has been doubled
      if (recorder.getState() != AudioRecord.STATE_INITIALIZED)
      {
        logger.logMessage(LogZone.LOG_ERROR,"AudioRecord Initialization failed!!!");
        errorCode = "AudioRecord_Initialization_Failed";
      }
      else
      {
        record = new Thread()
        {
          public void run()
          {
            audioRecord();
          }
        };
      }
    }
    catch (IllegalArgumentException e)
    {
      logger.logMessage(LogZone.LOG_ERROR,"AudioRecord initialization failed"+e);
      errorCode = "AudioRecord_Initialization_Failed";
    }
    return errorCode;
  }

  //-----------------------------------------------------------------------------------------------------------------------------
  // Caller:         InputtAudioAdapter.
  // Description:    The function starts the capturing of audio samples at an Audio Input Interface instance
  // Returns:        Error in text form.
  //-----------------------------------------------------------------------------------------------------------------------------
  public String start()
  {
    logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Entering function audioIn::start");
    try
    {
      record.start();
    }
    catch (IllegalThreadStateException e)
    {
      logger.logMessage(LogZone.LOG_ERROR,"audioin record thread failed"+e);
      logger.logMessage(LogZone.LOG_ERROR,"audioin record thread failed"+record.getState());
      errorCode = "AudioIn_Record_Thread_Failed";
    }
    return errorCode;
  }

  //---------------------------------------------------------------------------------------------------------------------
  // Caller:         audioIn::start
  // Description:    Thread function that reads the audio input data from the input device and calls onAudioDataCaptured to
  //                 send the audio input data.
  //---------------------------------------------------------------------------------------------------------------------
  private void audioRecord()
  {
    logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Entering function audioIn::audioRecord");
    try
    {
      if(null != recorder && AudioRecord.STATE_UNINITIALIZED != recorder.getState())
      {
        recorder.startRecording();//Starting audiorecord recording
        while(state.STATE_STARTED == audio_state)
        {
          short[] tempBuffer = new short[samplesPerChannel]; //buffer to store audiodata read from AudioRecord
          int audioData = recorder.read(tempBuffer, 0, samplesPerChannel ); //reading data from audiorecord buffer
          if(AudioRecord.ERROR_INVALID_OPERATION == audioData)
          {
            errorCode = "AudioRecord_Not_Properly_Initialized";
            logger.logMessage(LogZone.LOG_ERROR,"AudioRecord read failed with error "+errorCode);
            break;
          }

          else if(AudioRecord.ERROR_BAD_VALUE == audioData)
          {
            errorCode = "AudioRecord_Read_Parameters_Invalid";
            logger.logMessage(LogZone.LOG_ERROR,"AudioRecord read failed with error "+errorCode);
            break;
          }

          else if(AudioRecord.ERROR_DEAD_OBJECT == audioData)
          {
            errorCode = "AudioRecord_Object_Invalid";
            logger.logMessage(LogZone.LOG_ERROR,"AudioRecord read failed with error "+errorCode);
            break;
          }

          else if(AudioRecord.ERROR == audioData)
          {
            errorCode = "AudioRecord_Error";
            logger.logMessage(LogZone.LOG_ERROR,"AudioRecord read failed with error "+errorCode);
            break;
          }

          else if(null != listener)
          {
            IAudioInputAdapterListener.FlowState flowStateOut[] = {nuance.audio.IAudioInputAdapterListener.FlowState.NORMAL};
            listener.onAudioDataCaptured(tempBuffer, false, ResultCode.OK, flowStateOut);

            if (null != audioDataCallback) {
              byte[] bytes = new byte[2 * audioData];

              try {
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(tempBuffer, 0, audioData);
              } catch (Exception e) {
                e.printStackTrace();
              }

              audioDataCallback.onCapture(bytes, bytes.length);
            }
          }
          else
          {
            errorCode = "Invalid_Nuance_Audio_IAudioInputAdapterListener";
            logger.logMessage(LogZone.LOG_ERROR,"audioIn::audioRecord failed with error "+errorCode);
            break;
          }
        }
      }
      else
      {
        logger.logMessage(LogZone.LOG_ERROR,"AudioRecord object invalid");
        errorCode = "AudioRecord_Object_Invalid";
      }
    }
    catch (IllegalStateException e)
    {
      logger.logMessage(LogZone.LOG_ERROR,"AudioRecord startRecording failed"+e);
      errorCode = "AudioRecord_StartRecording_Failed";
    }
    logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Leaving function audioIn::audioRecord");
  }

  //-----------------------------------------------------------------------------------------------------------------------------
  // Caller:         InputtAudioAdapter.
  // Description:    The function stops the capturing of audio samples at an Audio Input Interface instance
  // Returns:        Error in text form.
  //-----------------------------------------------------------------------------------------------------------------------------
  public String stop()
  {
    logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Entering function audioIn::stop");
    try
    {
      if(null != recorder && AudioRecord.STATE_UNINITIALIZED != recorder.getState())
      {
        recorder.stop();//Stopping audiorecord recording
      }
      else
      {
        logger.logMessage(LogZone.LOG_ERROR,"AudioRecord object invalid");
        errorCode = "AudioRecord_Object_Invalid";
      }
    }
    catch (IllegalStateException e)
    {
      logger.logMessage(LogZone.LOG_ERROR,"AudioRecord stop failed"+e);
      errorCode = "AudioRecord_Stop_Failed";
    }
    return errorCode;
  }

  //------------------------------------------------------------------------------------------------
  // Caller:         InputtAudioAdapter.
  // Description:    The function deinitializes an Audio Input Interface instance
  // Returns:        Error in text form.
  //------------------------------------------------------------------------------------------------
  public String close()
  {
    if(null != record)
    {
      try
      {
        record.join();
      }
      catch (InterruptedException e)
      {
        logger.logMessage(LogZone.LOG_ERROR,"audioin record thread failed"+e);
        errorCode = "AudioIn_Record_Thread_Failed";
      }
    }
    else
    {
      logger.logMessage(LogZone.LOG_ERROR,"audioin record thread invalid");
      errorCode = "AudioIn_Record_Thread_Failed";
    }
    return errorCode;
  }

  //---------------------------------------------------------------------------------------------------------
  // Caller:       OutputAudioAdapter.
  // Description:    Returns a textual description of the last audio output adapter error.
  // Returns:      Error in text form.
  // Possible error text are:
  //   AudioRecord_Initialization_Failed
  //   Invalid_Nuance_Audio_IAudioInputAdapterListener
  //   Invalid_ChannelCount
  //   Invalid_SampleRate
  //   Invalid_SamplesPerChannel
  //   Invalid_State
  //   AudioIn_Record_Thread_Failed
  //   AudioRecord_Not_Properly_Initialized
  //   AudioRecord_Object_Invalid
  //   AudioRecord_Error
  //   Invalid_Nuance_Audio_IAudioInputAdapterListener
  //   AudioRecord_StartRecording_Failed
  //   AudioRecord_Stop_Failed
  //---------------------------------------------------------------------------------------------------------
  public String getErrorText()
  {
    logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Entering function audioOut::getErrorText");
    return errorCode;
  }

  //------------------------------------------------------------------------------------------------
  // Caller:         InputtAudioAdapter.
  // Description:    The function releases the resources held by audiorecord
  // Returns:        Error in text form.
  //------------------------------------------------------------------------------------------------
  public void releaseRecorder()
  {
    logger.logMessage(LogZone.LOG_INTERNAL_FUNC,"Entering function audioIn::releaseRecorder");
    if(null != recorder && AudioRecord.STATE_UNINITIALIZED != recorder.getState())
    {
      recorder.release();
    }
    else
    {
      logger.logMessage(LogZone.LOG_ERROR,"AudioRecord object invalid");
      errorCode = "AudioRecord_Object_Invalid";
    }
    logger.logModule.destroy();
  }
}
