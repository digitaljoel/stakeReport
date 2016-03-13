package jota.grande.stakereport;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Headers;

public class StakeReport {

  private static final String API_KEY_HEADER = "x-api-key: <the-api-key>";

  @SuppressWarnings("unchecked")
  public static void main( String[] args ) throws IOException {
    Retrofit retrofit  = new Retrofit.Builder()
        .baseUrl("<base url>")
        .addConverterFactory(JacksonConverterFactory.create())
        .build();
    ReportService reportService = retrofit.create(ReportService.class);
    Call<Map<Object, Object>> call = reportService.getReport();
    Map<Object, Object> report = call.execute().body();

    Map<String, String> stakeMap = getMapping( (List<Map<String,String>>)((Map<Object,Object>)report.get("mapping")).get("Items"));

    Map<String, StakeData> countMap = getStakeData( stakeMap,
      (List<Map<String, Object>>)((Map<Object,Object>)report.get("data")).get("Items"));

    try (FileWriter writer = new FileWriter("stakeReport" + getDateString() + ".csv" )) {
      StakeData outputData = countMap.get( "sandyutaheast" );
      outputData.wards.sort((o1,o2) -> o1.compareTo(o2));
      writeHeader( outputData.wards, writer );
      outputData.counts.keySet().stream().sorted(chapterComparator).forEach(chapter ->
        writeRow( chapter, outputData.wards, outputData.counts.get(chapter), writer )
      );
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static Map<String, StakeData> getStakeData( Map<String, String> stakeMap, List<Map<String, Object>> rows ) {
    // map of stake name to list of ChapterCounts.
    Map<String, StakeData> result = new HashMap<>();

    for ( Map<String, Object> row : rows ) {
      String stakeward = (String)row.get("stakeward");
      String chapter = (String)row.get("chapter");
      Integer readCount = (Integer)row.get("readCount");
      String[] splits = stakeward.split("-");
      if ( splits == null || splits.length != 2 ) {
        continue;
      }
      String stake = stakeMap.containsKey(splits[0]) ? stakeMap.get(splits[0]) : splits[0];
      String ward = stakeMap.containsKey(splits[1]) ? stakeMap.get(splits[1]) : splits[1];

      if ( !result.containsKey(stake)) {
        result.put(stake, new StakeData());
      }
      StakeData data = result.get(stake);

      if ( !data.wards.contains( ward )) {
        data.wards.add(ward);
      }

      if ( !data.counts.containsKey( chapter )) {
        data.counts.put(chapter, new ChapterCount());
      }

      ChapterCount count = data.counts.get( chapter );
      count.add(ward, readCount);

    }
    return result;
  }

  private static Map<String, String> getMapping( List<Map<String, String>> mapping) {
    Map<String, String> result = new HashMap<>();
    for ( Map<String, String> item : mapping) {
      result.put(item.get("input"), item.get("value"));
    }
    return result;
  }

  private static void writeHeader( List<String> wards, FileWriter writer ) throws IOException {
    writer.write( "chapter," + StringUtils.join(wards, ",") +  System.lineSeparator());
  }

  private static void writeRow( String chapter, List<String> wards, ChapterCount count, FileWriter writer ) {
    try {
      writer.write( chapter );
      for ( String ward : wards ) {
        Integer cur = count.countMap.get(ward);
        writer.write( "," + (cur == null ? "0" : cur ));
      }
      writer.write( System.lineSeparator());
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static String getDateString() {
     SimpleDateFormat format = new SimpleDateFormat("yyyyMMddkkmm");
     return format.format(new Date());
  }

  public static interface ReportService {
    @GET( "prod/stats" )
    @Headers({API_KEY_HEADER})
    Call<Map<Object, Object>> getReport();
  }

  public static class StakeData {
    // distinct list of wards in the stake
    public List<String> wards = new ArrayList<>();
    // all the chapter counts for the wards in the stake.
    private Map<String, ChapterCount> counts = new HashMap<>();
  }

  public static class ChapterCount {
    // map of ward to count
    public Map<String, Integer> countMap = new HashMap<>();
    public void add( String ward, Integer count ) {
      if( !countMap.containsKey( ward )) {
        countMap.put( ward, 0 );
      }
      countMap.put( ward, count + countMap.get(ward));
    }
  }
  public enum BOM_BOOK {
    FIRSTNEPHI( "1 Nephi" ),
    SECONDNEPHI( "2 Nephi" ),
    JACOB( "Jacob" ),
    ENOS( "Enos" ),
    JAROM( "Jarom" ),
    OMNI( "Omni" ),
    WORDSOFMORMON( "Words of Mormon" ),
    MOSIAH( "Mosiah" ),
    ALMA( "Alma" ),
    HELAMAN( "Helaman" ),
    THIRDNEPHI( "3 Nephi" ),
    FOURTHNEPHI( "4 Nephi" ),
    MORMON( "Mormon" ),
    ETHER( "Ether" ),
    MORONI( "Moroni" );
    private String id;
    BOM_BOOK( String id ) {
      this.id = id;
    }
    public static BOM_BOOK fromChapter( String chapter ) {
      String chapterPart = getChapterParts(chapter)[0];
      for ( BOM_BOOK book : BOM_BOOK.values() ) {
        if ( book.id.equals(chapterPart)) {
          return book;
        }
      }
      throw new IllegalArgumentException( chapter + " is not a BOM_BOOK" );
    }
    public static Integer getChapterNumber( String chapter ) {
      return Integer.parseInt(getChapterParts(chapter)[1]);
    }
    private static String[] getChapterParts( String chapter ) {
      String[] result = new String[2];
      String[] strings = chapter.split( " " );
      result[1] = strings[strings.length-1];
      String[] tmp = Arrays.copyOfRange(strings, 0, strings.length-1);
      result[0] = StringUtils.join(tmp, " " );
      return result;
    }
  }
  public static Comparator<String> chapterComparator = new Comparator<String>() {
    public int compare(String first, String second) {
      BOM_BOOK firstBook = BOM_BOOK.fromChapter(first);
      Integer firstChapter = BOM_BOOK.getChapterNumber(first);
      BOM_BOOK secondBook = BOM_BOOK.fromChapter(second);
      Integer secondChapter = BOM_BOOK.getChapterNumber(second);

      if ( firstBook == secondBook ) {
        return firstChapter == secondChapter ? 0 : firstChapter < secondChapter ? -1 : 1;
      }
      return firstBook.ordinal() < secondBook.ordinal() ? -1 : 1;
    }
  };
}
