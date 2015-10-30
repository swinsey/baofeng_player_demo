#include <map>
#include <cassert>
#include "mutex.h"
#include "video_codec.h"
#include "native_common_header.h"
#include "annexb2mp4.h"

extern "C" 
{
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
};

#ifndef ROUND
#define ROUND(x) ((uint64_t)((x) + 0.5))
#endif

//==============================================================================
typedef struct codec_handle
{
	AVCodecContext* m_codec_context;
	uint64_t        m_frame_index;
}codec_handle_t;

//==============================================================================
static video_encoder_t g_video_encoder_id = rand();
static video_decoder_t g_video_decoder_id = rand();
static std::map<video_encoder_t, codec_handle_t*> g_video_encoder_map;
static std::map<video_decoder_t, codec_handle_t*> g_video_decoder_map;
static mutex g_video_encoder_mutex; 
static mutex g_video_decoder_mutex;
static bool g_init_video_codec = false;

//==============================================================================
#ifdef __cplusplus
extern "C"
{
#endif

static codec_handle_t* create_h264_encoder_handle(
	uint16_t width, uint16_t height, uint16_t bitrate, uint16_t framerate)
{
	AVCodec* codec = avcodec_find_encoder(AV_CODEC_ID_H264);
	if(NULL == codec)
		return NULL;

	codec_handle_t* codec_handle = 
		(codec_handle_t*)malloc(sizeof(codec_handle_t));
	assert(codec_handle);
	if(NULL == codec_handle)
		return NULL;

	memset(codec_handle, 0, sizeof(codec_handle_t));
	bool result = false;
	do 
	{
		codec_handle->m_codec_context = avcodec_alloc_context3(codec);
		assert(codec_handle->m_codec_context);
		if(NULL == codec_handle->m_codec_context)
		{
			av_log(NULL, AV_LOG_ERROR, "Cannot alloc h264 encoder context\n");
			break;
		}

		codec_handle->m_codec_context->codec_type = AVMEDIA_TYPE_VIDEO;
		codec_handle->m_codec_context->pix_fmt = AV_PIX_FMT_YUV420P;
		codec_handle->m_codec_context->width = width;
		codec_handle->m_codec_context->height = height;
		codec_handle->m_codec_context->time_base.num = 1;
		codec_handle->m_codec_context->time_base.den = framerate;
		//codec_handle->m_codec_context->ticks_per_frame = 2;

		// modify by letion
		// ʹ��CABAC�ر��뼼��,Ϊ������΢�ı���ͽ�����ٶ���ʧ�����ǿ������10%-15%�ı�������
		codec_handle->m_codec_context->coder_type = FF_CODER_TYPE_AC;
		codec_handle->m_codec_context->me_method = 7;					// �˶����ķ�ʽ
		codec_handle->m_codec_context->me_range = 16;					// �˶����İ뾶
		codec_handle->m_codec_context->me_subpel_quality = 1;			// �˶�����������������ٶȵ�Ȩ��
		codec_handle->m_codec_context->noise_reduction = 1000;			// ִ�п���ȥ�롣�Դ�ֵΪ��ֵȷ������

		// ������ɫ���˶����ƺ�P֡��ģʽѡ��
		codec_handle->m_codec_context->me_cmp = FF_CMP_CHROMA;
		// ���þ���ʹ��I֡��IDR֡����ֵ�������任��⣩��
		codec_handle->m_codec_context->scenechange_threshold = 40;
		// ʹ�����������������������Ч��, 1�ṩ���ٶȺ�Ч�ʼ�Ϻõľ���
		codec_handle->m_codec_context->trellis = 1;

		codec_handle->m_codec_context->max_qdiff = 4;		// ������֡��֮֡������б���������ӵı仯��
		codec_handle->m_codec_context->qmin = 10;			// ��С���������ӡ�ȡֵ��Χ1-51
		codec_handle->m_codec_context->qmax = 51;			// �����������ӡ�ȡֵ��Χ1-51
		// ����������ѹ��������0.0��ζ�ź㶨�����ʣ�1.0��ζ�ź㶨������
		codec_handle->m_codec_context->qcompress = 0.6;

		/* bitrate */
		// CBR
		// ��������ı�����,������ABR(Average Birtate ģʽ
		//codec_handle->m_codec_context->bit_rate = bitrate * 1000;
		//codec_handle->m_codec_context->rc_min_rate = codec_handle->m_codec_context->bit_rate;	// �������С����
		//codec_handle->m_codec_context->rc_max_rate = codec_handle->m_codec_context->bit_rate;	// ������������
		//codec_handle->m_codec_context->bit_rate_tolerance = codec_handle->m_codec_context->bit_rate;
		//codec_handle->m_codec_context->rc_buffer_size = codec_handle->m_codec_context->bit_rate;
		//// ��ʼ�Ļ���ռ����
		//codec_handle->m_codec_context->rc_initial_buffer_occupancy = codec_handle->m_codec_context->rc_buffer_size*3/4;
		//codec_handle->m_codec_context->rc_buffer_aggressivity= (float)1.0;
		//codec_handle->m_codec_context->rc_initial_cplx= 0.5; 

		// VBR ��֪��Ϊʲô��һ֡�����ܲ�
		codec_handle->m_codec_context->flags |= CODEC_FLAG_QSCALE; 
		codec_handle->m_codec_context->bit_rate = bitrate * 1000;
		codec_handle->m_codec_context->rc_min_rate = codec_handle->m_codec_context->bit_rate * 0.5;
		codec_handle->m_codec_context->rc_max_rate = codec_handle->m_codec_context->bit_rate * 1.5;
		codec_handle->m_codec_context->rc_buffer_size = codec_handle->m_codec_context->rc_max_rate;


		codec_handle->m_codec_context->gop_size = 15;		// �ؼ�֡�������֡��
		codec_handle->m_codec_context->keyint_min = 10;		// �ؼ�֡����С���֡��
		codec_handle->m_codec_context->max_b_frames = 0;		// ���B֡��.
		codec_handle->m_codec_context->refs = 1;				// B��P֡��ǰԤ��ο���֡����ȡֵ��Χ1-16��

		//// Set Option
		AVDictionary *param = NULL;
		// һЩ��ѹ��Ч�ʺ�����ʱ����ƽ���Ԥ��ֵ��
		av_dict_set(&param, "preset", "veryfast", 0);
		av_dict_set(&param, "tune", "zerolatency", 0);
		// ��������ļ���profile����ѡ���ܱ�֤���profile���ݵ���Ƶ��
		av_dict_set(&param, "profile", "main", 0);
		// ����H.264��׼�е�����ȥ���˾������Ǹ��Լ۱Ⱥܸߵ�ѡ��
		av_dict_set(&param, "deblock", "-1:-1", 0);
		//av_dict_set(&param, "crf", "24", 0);
		av_dict_set(&param, "threads", "auto", 0);

		// 
		codec_handle->m_codec_context->flags |= CODEC_FLAG_GLOBAL_HEADER;

		if (avcodec_open2(codec_handle->m_codec_context, codec, &param) < 0)
		{
			av_log(NULL, AV_LOG_ERROR, "Cannot open h264 encoder context\n");
			break;
		}
		result = true;
	} while (false);

	if(!result)
	{
		if(NULL != codec_handle->m_codec_context)
		{
			avcodec_free_context(&codec_handle->m_codec_context);
			codec_handle->m_codec_context = NULL;
		}

		free(codec_handle);
		codec_handle = NULL;
	}
	return codec_handle;
}

static uint32_t codec_handle_encode(codec_handle_t* codec_handle, 
	const uint8_t* in, uint32_t in_size, uint8_t* out, uint32_t out_size, 
	video_frame_type_t* type)
{
	assert(codec_handle);
	if(NULL == codec_handle || NULL == codec_handle->m_codec_context)
		return 0;

	// alloc frame memory
	AVFrame* frame = av_frame_alloc();// avcodec_alloc_frame();
	assert(frame);
	if(NULL == frame)
		return 0;

	// fill frame data
	avpicture_fill((AVPicture*)frame, (uint8_t*)in, AV_PIX_FMT_YUV420P, 
		codec_handle->m_codec_context->width, 
		codec_handle->m_codec_context->height);

	uint32_t result = 0;
	AVPacket packet;
	av_init_packet(&packet);

	packet.data = (uint8_t*)out;
	packet.size = out_size;

	do 
	{
		int got_picture = 0;
		//Encode ����
		int ret = avcodec_encode_video2(codec_handle->m_codec_context, 
			&packet, frame, &got_picture);
		if (ret < 0)
		{
			av_log(NULL, AV_LOG_ERROR, "H264 encode data failed\n");
			break;
		}

		if(got_picture)
		{
			if(NULL != type)
			{
				if(packet.flags & AV_PKT_FLAG_KEY)
					*type = VIDEO_FRAME_I;
				else
					*type = VIDEO_FRAME_P;
			}

			result = packet.size;
		}
	} while (false);

	av_free_packet(&packet);
	av_free(frame);
	return result;
}

static codec_handle_t* create_h264_decoder_handle(uint16_t width, uint16_t height)
{
	AVCodec* codec = avcodec_find_decoder(AV_CODEC_ID_H264);
	if(NULL == codec)
		return NULL;

	codec_handle_t* codec_handle = 
		(codec_handle_t*)malloc(sizeof(codec_handle_t));
	assert(codec_handle);
	if(NULL == codec_handle)
		return NULL;

	memset(codec_handle, 0, sizeof(codec_handle_t));
	bool result = false;
	do 
	{
		codec_handle->m_codec_context = avcodec_alloc_context3(codec);
		assert(codec_handle->m_codec_context);
		if(NULL == codec_handle->m_codec_context)
		{
			av_log(NULL, AV_LOG_ERROR, "Cannot alloc h264 decoder context\n");
			break;
		}

		if(avcodec_open2(codec_handle->m_codec_context, codec, NULL) < 0)
		{
			av_log(NULL, AV_LOG_ERROR, "Cannot open h264 decoder context\n");
			break;
		}

		result = true;
	}while(false);

	if(!result)
	{
		if(NULL != codec_handle->m_codec_context)
		{
			avcodec_free_context(&codec_handle->m_codec_context);
			codec_handle->m_codec_context = NULL;
		}

		free(codec_handle);
		codec_handle = NULL;
	}
	return codec_handle;
}

static uint32_t codec_handle_decode(codec_handle_t* codec_handle, 
	const uint8_t* in, uint32_t in_size, uint8_t* out, uint32_t out_size)
{
	assert(codec_handle);
	if(NULL == codec_handle || NULL == codec_handle->m_codec_context)
		return 0;

	AVPacket packet;
	av_init_packet(&packet);

	packet.data = (uint8_t*)in;
	packet.size = in_size;

	// alloc frame memory
	AVFrame* frame = av_frame_alloc();// avcodec_alloc_frame();
	assert(frame);
	if(NULL == frame)
	{
		av_free_packet(&packet);
		return 0;
	}

	uint32_t result = 0;
	do 
	{
		int got_frame;
		if(avcodec_decode_video2(codec_handle->m_codec_context, frame, 
			&got_frame, &packet) < 0)
		{
			av_log(NULL, AV_LOG_ERROR, "H264 decode data failed\n");
			break;
		}

		uint32_t width = codec_handle->m_codec_context->width;
		uint32_t height = codec_handle->m_codec_context->height;

		if(got_frame)
		{
			// ��תYUVͼ��  
			frame->data[0]  += frame->linesize[0] * (height - 1);  
			frame->linesize[0] *= -1;                     
			frame->data[1]  += frame->linesize[1] * (height / 2 - 1);  
			frame->linesize[1] *= -1;  
			frame->data[2]  += frame->linesize[2] * (height / 2 - 1);  
			frame->linesize[2] *= -1;  

			int frame_size = avpicture_get_size(AV_PIX_FMT_YUV420P, width, height);
			if(frame_size <= out_size)
			{
				AVPicture image;
				if(avpicture_fill(&image, out, AV_PIX_FMT_YUV420P, width, height) < 0)
				{
					av_log(NULL, AV_LOG_ERROR, "H264 decode avpicture_fill failed\n");
					break;
				}

				av_picture_copy(&image, (AVPicture*)frame, AV_PIX_FMT_YUV420P, width, height);
				result = frame_size;
			}
		}

	} while (false);

	av_free_packet(&packet);
	av_free(frame);
	return result;
}

static void destroy_codec_handle(codec_handle_t* codec_handle)
{
	assert(codec_handle);
	if(NULL != codec_handle)
	{
		if(NULL != codec_handle->m_codec_context)
		{
			avcodec_close(codec_handle->m_codec_context);
			avcodec_free_context(&codec_handle->m_codec_context);
			codec_handle->m_codec_context = NULL;
		}

		free(codec_handle);
		codec_handle = NULL;
	}
}

//==============================================================================
video_encoder_t VIDEO_CODEC_API 
	create_video_encoder(video_codec_type_t codec_type, 
	uint16_t width, uint16_t height, 
	uint16_t bitrate, uint16_t framerate)
{
	assert(codec_type == VIDEO_CODEC_H264);
	if(codec_type != VIDEO_CODEC_H264)
	{
		av_log(NULL, AV_LOG_ERROR, "Video codec only support H264 encoder\n");
		return INVALID_VIDEO_CODEC;
	}

	if(!g_init_video_codec)
	{
		/* register all the codecs */  
		avcodec_register_all();  
		g_init_video_codec = true;
	}

	video_encoder_t encoder_id = INVALID_VIDEO_CODEC;
	codec_handle_t* codec_handle = create_h264_encoder_handle(
		width, height, bitrate, framerate);
	if(NULL != codec_handle)
	{
		auto_lock lock(g_video_encoder_mutex);
		// �˴�δ���id�Ƿ��ظ����ǻ���id���ƵĿ����Լ���Ϊ���ǰ����
		encoder_id = ++g_video_encoder_id;
		g_video_encoder_map[encoder_id] = codec_handle;
	}
	return encoder_id;
}

int32_t VIDEO_CODEC_API 
	destroy_video_encoder(video_encoder_t handle)
{
	int32_t result = -1;

	auto_lock lock(g_video_encoder_mutex);
	std::map<video_encoder_t, codec_handle_t*>::iterator iter = 
		g_video_encoder_map.find(handle);
	if(iter != g_video_encoder_map.end())
	{
		codec_handle_t* codec_handle = iter->second;
		assert(codec_handle);
		if(NULL != codec_handle)
		{
			destroy_codec_handle(codec_handle);
			codec_handle = NULL;
		}
		g_video_encoder_map.erase(iter);
	}
	return result;
}

uint32_t VIDEO_CODEC_API 
	video_encoder_extradata_size(video_encoder_t handle)
{
	uint32_t result = 0;

	auto_lock lock(g_video_encoder_mutex);
	std::map<video_encoder_t, codec_handle_t*>::iterator iter = 
		g_video_encoder_map.find(handle);
	if(iter != g_video_encoder_map.end())
	{
		codec_handle_t* codec_handle = iter->second;
		assert(codec_handle);
		if(NULL != codec_handle)
		{
			result = codec_handle->m_codec_context->extradata_size;
		}
	}
	return result;
}

uint32_t VIDEO_CODEC_API 
	video_encoder_extradata(video_encoder_t handle, 
	uint8_t* extradata, uint32_t extradata_size)
{
	assert(extradata);
	assert(extradata_size);
	if(NULL == extradata || 0 == extradata_size)
		return 0;

	uint32_t result = 0;

	auto_lock lock(g_video_encoder_mutex);
	std::map<video_encoder_t, codec_handle_t*>::iterator iter = 
		g_video_encoder_map.find(handle);
	if(iter != g_video_encoder_map.end())
	{
		codec_handle_t* codec_handle = iter->second;
		assert(codec_handle);
		if(NULL != codec_handle)
		{
			uint32_t size = codec_handle->m_codec_context->extradata_size;
			if(size <= extradata_size)
			{
				memcpy(extradata, codec_handle->m_codec_context->extradata, size);
				result = size;
			}
		}
	}
//	uint8_t* spspps = NULL;
//	int spspps_size = result;
//	if(annexb2avcc(extradata, &spspps, &spspps_size) >= 0)
//	{
//		memcpy(extradata, spspps, spspps_size);
//		result = spspps_size;
////		av_free(spspps);
//	}

	return result;
}

uint32_t VIDEO_CODEC_API 
	video_encoder_encode(video_encoder_t handle, 
	const uint8_t* in, uint32_t in_size, uint8_t* out, uint32_t out_size, 
	video_frame_type_t* type)
{
	uint32_t result = 0;

	auto_lock lock(g_video_encoder_mutex);
	std::map<video_encoder_t, codec_handle_t*>::iterator iter = 
		g_video_encoder_map.find(handle);
	if(iter != g_video_encoder_map.end())
	{
		codec_handle_t* codec_handle = iter->second;
		assert(codec_handle);
		if(NULL != codec_handle)
		{
			result = codec_handle_encode(codec_handle, 
				in, in_size, out, out_size, type);
		}
	}
	return result;
}

video_decoder_t VIDEO_CODEC_API 
	create_video_decoder(uint32_t codec_type, uint16_t width, uint16_t height)
{
	assert(codec_type == VIDEO_CODEC_H264);
	if(codec_type != VIDEO_CODEC_H264)
	{
		av_log(NULL, AV_LOG_ERROR, "Video codec only support H264 decoder\n");
		return INVALID_VIDEO_CODEC;
	}

	if(!g_init_video_codec)
	{
		/* register all the codecs */  
		avcodec_register_all();  
		g_init_video_codec = true;
	}

	video_decoder_t decoder_id = INVALID_VIDEO_CODEC;
	codec_handle_t* codec_handle = create_h264_decoder_handle(width, height);
	if(NULL != codec_handle)
	{
		auto_lock lock(g_video_decoder_mutex);
		// �˴�δ���id�Ƿ��ظ����ǻ���id���ƵĿ����Լ���Ϊ���ǰ����
		decoder_id = ++g_video_decoder_id;
		g_video_decoder_map[decoder_id] = codec_handle;
	}
	return decoder_id;
}

int32_t VIDEO_CODEC_API 
	destroy_video_decoder(video_decoder_t handle)
{
	int32_t result = -1;

	auto_lock lock(g_video_decoder_mutex);
	std::map<video_decoder_t, codec_handle_t*>::iterator iter = 
		g_video_decoder_map.find(handle);
	if(iter != g_video_decoder_map.end())
	{
		codec_handle_t* codec_handle = iter->second;
		assert(codec_handle);
		if(NULL != codec_handle)
		{
			destroy_codec_handle(codec_handle);
			codec_handle = NULL;
		}
		g_video_decoder_map.erase(iter);
	}
	return result;
}

uint32_t VIDEO_CODEC_API 
	video_decoder_decode(video_decoder_t handle, 
	const uint8_t* in, uint32_t in_size, uint8_t* out, uint32_t out_size)
{
	uint32_t result = 0;

	auto_lock lock(g_video_decoder_mutex);
	std::map<video_decoder_t, codec_handle_t*>::iterator iter = 
		g_video_decoder_map.find(handle);
	if(iter != g_video_decoder_map.end())
	{
		codec_handle_t* codec_handle = iter->second;
		assert(codec_handle);
		if(NULL != codec_handle)
		{
			result = codec_handle_decode(codec_handle, in, in_size, 
				out, out_size);
		}
	}
	return result;
}

//==============================================================================
#ifdef __cplusplus
}
#endif
