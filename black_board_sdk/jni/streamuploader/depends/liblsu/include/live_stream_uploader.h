///============================================================================
/// @file :     live_stream_uploader.h
///	@brief :    ��������Ƶֱ���ϴ�ģ��ӿ�
/// @version :  1.0
/// @date :     2015-08-19
///============================================================================

#pragma once

#include <cstdint>

#ifdef __cplusplus
extern "C"
{
#endif

//=============================================================================
// ����Լ����������ʽ����

#ifdef _WIN32
#  if defined(LIBLSU_BUILDING_SHARED) || defined(LIBLSU_USING_SHARED)
#    define LIBLSU_API __stdcall
#  else
#    define LIBLSU_API
#  endif
#else
#  define LIBLSU_API
#endif

#ifdef _WIN32
#  if defined(LIBLSU_BUILDING_SHARED)
#    define LIBLSU_EXTERN  __declspec(dllexport)
#  elif defined(LIBLSU_USING_SHARED)
#    define LIBLSU_EXTERN  __declspec(dllimport)
#  else
#    define LIBLSU_EXTERN /* nothing */
#  endif
#elif __GNUC__ >= 4
#  define LIBLSU_EXTERN __attribute__((visibility("default")))
#else
#  define LIBLSU_EXTERN /* nothing */
#endif

//=============================================================================
// �����붨��

#define LIBLSU_NO_ERROR                    0
#define LIBLSU_INVALID_PARAM             (-1)   // �Ƿ�����
#define LIBLSU_APPLY_UPLOAD_ADDR_FAILED  (-2)   // �����ϴ���ַʧ��
#define LIBLSU_CONNECT_UPLOAD_SRV_FAILED (-3)   // �����ϴ�������ʧ��
#define LIBLSU_OPEN_CHANNEL_FAILED       (-4)   // ��ͨ��ʧ��
#define LIBLSU_CHANNEL_DISABLED          (-5)   // ͨ������ֹ

//=============================================================================
// ���Ͷ���

// ״ֵ̬
enum LIBLSU_STATUS
{
	LIBLSU_STATUS_OPENED = 1,           // ��ͨ���ɹ�
	LIBLSU_STATUS_CLOSED = 2,           // �ر�ͨ���ɹ�
	LIBLSU_STATUS_ERROR  = 3,           // ��������
};

// ��Ƶ�����ʽ
enum LIBLSU_AUDIO_CODEC_TYPE
{
	LIBLSU_ACT_AAC = 0,                 // ��Ƶ AAC �����ʽ
	LIBLSU_ACT_MP3 = 1,                 // ��Ƶ MP3 �����ʽ
};

// ��Ƶ�����ʽ
enum LIBLSU_VIDEO_CODEC_TYPE
{
	LIBLSU_VCT_H264 = 0,                // ��Ƶ H264 �����ʽ
};

// ֡����
enum LIBLSU_FRAME_TYPE
{
	LIBLSU_FRAME_NON_KEY = 0,           // �ǹؼ�֡
	LIBLSU_FRAME_KEY = 1,               // �ؼ�֡
};

// ��Ƶ��ʽ
struct liblsu_audio_meta_data
{
	LIBLSU_AUDIO_CODEC_TYPE codec_type; // �����ʽ
	int32_t sample_rate;                // ��Ƶ������
	int32_t data_rate;                  // ��Ƶ���� (bps)
	int16_t channel_count;              // ��Ƶͨ������ (������/������)
	int8_t sample_size;                 // ��Ƶ������С (������λ�����������)
	int16_t extra_data_size;            // ��չ���ݳ���
	char *extra_data;                   // ��չ���� (��AAC��Ƶ���и��ֶ�)
};

// ��Ƶ��ʽ
struct liblsu_video_meta_data
{
	LIBLSU_VIDEO_CODEC_TYPE codec_type; // �����ʽ
	int16_t width;                      // ��Ƶ���
	int16_t height;                     // ��Ƶ�߶�
	int16_t frame_rate;                 // ��Ƶ֡��
	int32_t data_rate;                  // ��Ƶ���� (bps)
	int16_t extra_data_size;            // ��չ���ݳ���
	char *extra_data;                   // ��չ���� (��H264��Ƶ���и��ֶ�)
};

// ״̬�ص�����
typedef void (LIBLSU_API *liblsu_status_callback)(LIBLSU_STATUS status, void *arg);

//=============================================================================
// �ӿڶ���

/**
 * ֱ���ϴ���ʼ��
 * @param[in] max_buffer_size ��󻺴��ֽ��� (���������ö�������, Ϊ0��ʾ������)
 * @param[in] log_dir ָ����־���Ŀ¼ (��Ŀ¼�����ڽ��Զ�����)
 * @param[in] status_cb ״̬�ص� (�򿪹ر�ͨ���ͳ���ȶ�ͨ��״̬�ص�֪ͨ������)
 * @param[in] status_cb_arg ״̬�ص��ĸ��Ӳ���
 *
 * @remarks
 *  ��ʼ������������ɣ��������Ҳ��������ص���
 */
LIBLSU_EXTERN void LIBLSU_API liblsu_init(
	int max_buffer_size,
	const char *log_dir,
	liblsu_status_callback status_cb,
	void *status_cb_arg
	);

/**
 * ���ϴ�ͨ��
 * @param[in] channel_id ֱ��Ƶ��ID
 * @param[in] audio_format ��Ƶ��ʽ (����������Ƶ����Ϊ NULL)
 * @param[in] video_format ��Ƶ��ʽ (����Ϊ NULL)
 *
 * @remarks
 *  1. ���ô˺�����ִ���ϴ�ǰ��׼������ (ѡ������������ӷ���������ͨ��)��
 *  2. ��ͨ����ɻ������󣬽�ͨ��״̬�ص�֪ͨ�����ߡ�
 *  3. �����߸������ audio_format �� video_format ���ڴ������ͷš��������غ󼴿��ͷ����ǡ�
 *  4. ÿ�ε��� liblsu_open() ʱ�ᱣ֤�ڴ�֮ǰ���в��������Ļص���ȡ����
 */
LIBLSU_EXTERN void LIBLSU_API liblsu_open(
	const char *channel_id,
	const liblsu_audio_meta_data *audio_format,
	const liblsu_video_meta_data *video_format
	);

/**
 * �ϴ���Ƶ֡����
 * @param[in] dts  ����ʱ��� (����)
 * @param[in] data ֡����
 * @param[in] size ֡�����ֽ���
 */
LIBLSU_EXTERN void LIBLSU_API liblsu_upload_audio_frame(
	int dts, const char *data, int size);

/**
 * �ϴ���Ƶ֡����
 * @param[in] frame_type ��Ƶ֡���� (�� enum LIBLSU_FRAME_TYPE)
 * @param[in] dts  ����ʱ��� (����)
 * @param[in] pts  ����ʱ��� (����)
 * @param[in] data ֡����
 * @param[in] size ֡�����ֽ���
 */
LIBLSU_EXTERN void LIBLSU_API liblsu_upload_video_frame(
	LIBLSU_FRAME_TYPE frame_type, int dts, int pts, const char *data, int size);

/**
 * �ر��ϴ�ͨ��
 *
 * @remarks
 *  1. ���ô˺������ر��ϴ�ͨ�������ٽ��ܺ����ϴ����ݡ�
 *  2. �ر�ͨ����ɺ�ͨ��״̬�ص�֪ͨ�����ߡ�
 */
LIBLSU_EXTERN void LIBLSU_API liblsu_close();

/**
 * ȡ�ô�����
 */
LIBLSU_EXTERN int LIBLSU_API liblsu_get_last_error();

/**
 * ֱ���ϴ�������
 */
LIBLSU_EXTERN void LIBLSU_API liblsu_uninit();

//=============================================================================

#ifdef __cplusplus
};
#endif
