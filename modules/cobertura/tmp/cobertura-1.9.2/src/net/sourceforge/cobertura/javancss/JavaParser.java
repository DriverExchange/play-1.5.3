/*
 * Cobertura - http://cobertura.sourceforge.net/
 *
 * This file was taken from JavaNCSS
 * http://www.kclee.com/clemens/java/javancss/
 * Copyright (C) 2000 Chr. Clemens Lee <clemens a.t kclee d.o.t com>
 *
 * Cobertura is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 *
 * Cobertura is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Cobertura; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */
package net.sourceforge.cobertura.javancss;

import java.util.Hashtable;
import java.util.Vector;

public class JavaParser implements JavaParserConstants {

    private boolean _bReturn         = false;
    private int     _ncss            = 0;     // general counter
    private int     _loc             = 0;
    private int     _cyc             = 1;
    private int     _localCases      = 0;
    private String  _sName           = "";    // name of last token
    private String  _sParameter      = "";
    private String  _sPackage        = "";
    private String  _sClass          = "";
    private String  _sFunction       = "";
    private int     _functions       = 0;     // number of functions in this class
    private int     _topLevelClasses = 0;
    private int     _classes         = 0;
    private int     _classLevel      = 0;
    private int     _anonClassCount  = 1;

    private int     _jvdcLines = 0;           // added by SMS
    private int     _jvdc      = 0;
    private boolean _bPrivate  = true;//false;        // added by SMS
    private boolean _bPublic   = true;        // added by SMS

    /**
     * For each class the number of formal
     * comments in toplevel methods, constructors, inner
     * classes, and for the class itself are counted.
     * The top level comment has to be directly before
     * the class definition, not before the package or
     * import statement as it is often seen in source code
     * examples (at the beginning of your source files you
     * should instead put your copyright notice).
     */
    private int    _javadocs   = 0;              // global javadocs
    private Vector _vFunctions = new Vector();   // holds the statistics for each method

    /** 
     * Metrices for each class/interface are stored in this
     * vector.
     */
    private Vector _vClasses = new Vector();
    private Vector _vImports = new Vector();
    private Object[] _aoPackage = null;
    private Hashtable _htPackage = new Hashtable();
    private PackageMetric _pPackageMetric;

    private Token _tmpToken = null;
    /** Argh, too much of a state machine. */
    private Token _tmpResultToken = null;

    private String _formatPackage(String sPackage_) {
        if (sPackage_.equals("")) {
            return ".";
        }

        return sPackage_.substring(0, sPackage_.length() - 1);
    }

    public int getNcss() {
        return _ncss;
    }

    public int getLOC() {
        return _loc;
    }

    // added by SMS
    public int getJvdc() {
        return _jvdc;
    }

    /*public int getTopLevelClasses() {
      return _topLevelClasses;
      }*/

    public Vector getFunction() {
        return _vFunctions;
    }

    /**
     * @return Top level classes in sorted order
     */
    public Vector getObject() {
        return _vClasses;
    }

    /**
     * @return The empty package consists of the name ".".
     */
    public Hashtable getPackage() {
        return _htPackage;
    }

    public Vector getImports() {
        return _vImports;
    }

    /**
     * name, beginLine, ...
     */
    public Object[] getPackageObjects() {
        return _aoPackage;
    }

    /**
     * if javancss is used with cat *.java a long
     * input stream might get generated, so line
     * number information in case of an parse exception
     * is not very useful.
     */
    public String getLastFunction() {
        return _sPackage + _sClass + _sFunction;
    }

   /**
    * Class to hold modifiers.
    */
   static public final class ModifierSet
   {
     /* Definitions of the bits in the modifiers field.  */
     public static final int PUBLIC = 0x0001;
     public static final int PROTECTED = 0x0002;
     public static final int PRIVATE = 0x0004;
     public static final int ABSTRACT = 0x0008;
     public static final int STATIC = 0x0010;
     public static final int FINAL = 0x0020;
     public static final int SYNCHRONIZED = 0x0040;
     public static final int NATIVE = 0x0080;
     public static final int TRANSIENT = 0x0100;
     public static final int VOLATILE = 0x0200;
     public static final int STRICTFP = 0x1000;

     /** A set of accessors that indicate whether the specified modifier
         is in the set. */

     public boolean isPublic(int modifiers)
     {
       return (modifiers & PUBLIC) != 0;
     }

     public boolean isProtected(int modifiers)
     {
       return (modifiers & PROTECTED) != 0;
     }

     public boolean isPrivate(int modifiers)
     {
       return (modifiers & PRIVATE) != 0;
     }

     public boolean isStatic(int modifiers)
     {
       return (modifiers & STATIC) != 0;
     }

     public boolean isAbstract(int modifiers)
     {
       return (modifiers & ABSTRACT) != 0;
     }

     public boolean isFinal(int modifiers)
     {
       return (modifiers & FINAL) != 0;
     }

     public boolean isNative(int modifiers)
     {
       return (modifiers & NATIVE) != 0;
     }

     public boolean isStrictfp(int modifiers)
     {
       return (modifiers & STRICTFP) != 0;
     }

     public boolean isSynchronized(int modifiers)
     {
       return (modifiers & SYNCHRONIZED) != 0;
     }

     public boolean isTransient(int modifiers)
      {
       return (modifiers & TRANSIENT) != 0;
     }

     public boolean isVolatile(int modifiers)
     {
       return (modifiers & VOLATILE) != 0;
     }

     /**
      * Removes the given modifier.
      */
     static int removeModifier(int modifiers, int mod)
     {
        return modifiers & ~mod;
     }
   }

/*****************************************
 * THE JAVA LANGUAGE GRAMMAR STARTS HERE *
 *****************************************/

/*
 * Program structuring syntax follows.
 */
  final public void CompilationUnit() throws ParseException {
    int oldNcss = 0;

    // added by SMS
    int oldFormal = 0;
    int oldSingle = 0;
    int oldMulti  = 0;

    token_source._iSingleComments = 0;
    token_source._iMultiComments = 0;
    token_source._iFormalComments = 0;

    token_source._iMultiCommentsLast = 0;

    _bPrivate = true;
                _sPackage = "";
                _pPackageMetric = new PackageMetric();      // this object manages the metrics

    if (jj_2_1(2147483647)) {
      PackageDeclaration();
    } else {
      ;
    }
    label_1:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case IMPORT:
        ;
        break;
      default:
        jj_la1[0] = jj_gen;
        break label_1;
      }
      ImportDeclaration();
    }
    label_2:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case CLASS:
      case ENUM:
      case FINAL:
      case INTERFACE:
      case NATIVE:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case STATIC:
      case TESTAAAA:
      case SYNCHRONIZED:
      case TRANSIENT:
      case VOLATILE:
      case SEMICOLON:
      case AT:
        ;
        break;
      default:
        jj_la1[1] = jj_gen;
        break label_2;
      }
      TypeDeclaration();
    }
             // Package classes and functions are set inside
             // class and interface bodies.
             _pPackageMetric.ncss = _ncss;

             // added by SMS
             _pPackageMetric.javadocsLn = token_source._iFormalComments;
             _pPackageMetric.singleLn   = token_source._iSingleComments;
             _pPackageMetric.multiLn    = token_source._iMultiComments;
             //

             _htPackage.put(_formatPackage(_sPackage),
                            _pPackageMetric);
    label_3:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case IMPORT:
      case PACKAGE:
      case AT:
        ;
        break;
      default:
        jj_la1[2] = jj_gen;
        break label_3;
      }
      oldNcss = _ncss;
      _sPackage = "";
      _pPackageMetric = new PackageMetric();

      // added by SMS
      oldFormal = token_source._iFormalComments;
      oldSingle = token_source._iSingleComments;
      oldMulti  = token_source._iMultiComments;
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PACKAGE:
      case AT:
        PackageDeclaration();
        break;
      case IMPORT:
        ImportDeclaration();
        break;
      default:
        jj_la1[3] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      label_4:
      while (true) {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case IMPORT:
          ;
          break;
        default:
          jj_la1[4] = jj_gen;
          break label_4;
        }
        ImportDeclaration();
      }
      label_5:
      while (true) {
        TypeDeclaration();
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case ABSTRACT:
        case CLASS:
        case ENUM:
        case FINAL:
        case INTERFACE:
        case NATIVE:
        case PRIVATE:
        case PROTECTED:
        case PUBLIC:
        case STATIC:
        case TESTAAAA:
        case SYNCHRONIZED:
        case TRANSIENT:
        case VOLATILE:
        case SEMICOLON:
        case AT:
          ;
          break;
        default:
          jj_la1[5] = jj_gen;
          break label_5;
        }
      }
      // Package classes and functions are set inside
      // class and interface bodies.
      _pPackageMetric.ncss = _ncss - oldNcss;

      // added by SMS
      _pPackageMetric.javadocsLn = token_source._iFormalComments - oldFormal;
      _pPackageMetric.singleLn   = token_source._iSingleComments - oldSingle;
      _pPackageMetric.multiLn    = token_source._iMultiComments  - oldMulti;
      //

      PackageMetric pckmPrevious = (PackageMetric)_htPackage.
             get(_formatPackage(_sPackage));
      _pPackageMetric.add(pckmPrevious);
      _htPackage.put(_formatPackage(_sPackage),
                     _pPackageMetric);
    }
    jj_consume_token(0);
             Token pToken = getToken(1);
             _loc = pToken.endLine;
  }

  final public void ImportUnit() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case PACKAGE:
    case AT:
      PackageDeclaration();
      break;
    default:
      jj_la1[6] = jj_gen;
      ;
    }
    label_6:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case IMPORT:
        ;
        break;
      default:
        jj_la1[7] = jj_gen;
        break label_6;
      }
      ImportDeclaration();
    }
    label_7:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case FINAL:
      case PUBLIC:
      case TESTAAAA:
      case SYNCHRONIZED:
        ;
        break;
      default:
        jj_la1[8] = jj_gen;
        break label_7;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
        jj_consume_token(ABSTRACT);
        break;
      case FINAL:
        jj_consume_token(FINAL);
        break;
      case PUBLIC:
        jj_consume_token(PUBLIC);
        break;
      case SYNCHRONIZED:
        jj_consume_token(SYNCHRONIZED);
        break;
      case TESTAAAA:
        jj_consume_token(TESTAAAA);
        break;
      default:
        jj_la1[9] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case CLASS:
      jj_consume_token(CLASS);
      break;
    case INTERFACE:
      jj_consume_token(INTERFACE);
      break;
    default:
      jj_la1[10] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void PackageDeclaration() throws ParseException {
    int beginLine = 1;
    int beginColumn = 1;
    label_8:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AT:
        ;
        break;
      default:
        jj_la1[11] = jj_gen;
        break label_8;
      }
      Annotation();
    }
    jj_consume_token(PACKAGE);
      _anonClassCount = 1;

      Token pToken = getToken( 0 );
      beginLine = pToken.beginLine ;
      beginColumn = pToken.beginColumn;
      _aoPackage = new Object[ 5 ];
    Name();
      _aoPackage[ 0 ] = _sName;
      _aoPackage[ 1 ] = new Integer( beginLine );
      _aoPackage[ 2 ] = new Integer( beginColumn );
    jj_consume_token(SEMICOLON);
      _aoPackage[ 3 ] = new Integer( getToken( 0 ).endLine );
      _aoPackage[ 4 ] = new Integer( getToken( 0 ).endColumn );
      _ncss++;
      _sPackage = (new String(_sName)) + ".";
  }

  final public void ImportDeclaration() throws ParseException {
    int beginLine = 1;
    int beginColumn = 1;
    Object[] aoImport = null;
    jj_consume_token(IMPORT);
      Token pToken = getToken( 0 );
      beginLine = pToken.beginLine ;
      beginColumn = pToken.beginColumn;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case STATIC:
      jj_consume_token(STATIC);
      break;
    default:
      jj_la1[12] = jj_gen;
      ;
    }
    Name();
      aoImport = new Object[ 5 ];
      aoImport[ 0 ] = _sName;
      aoImport[ 1 ] = new Integer( beginLine );
      aoImport[ 2 ] = new Integer( beginColumn );
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case DOT:
      jj_consume_token(DOT);
      jj_consume_token(STAR);
              aoImport[ 0 ] = aoImport[ 0 ].toString() + ".*";
      break;
    default:
      jj_la1[13] = jj_gen;
      ;
    }
    jj_consume_token(SEMICOLON);
      aoImport[ 3 ] = new Integer( getToken( 0 ).endLine );
      aoImport[ 4 ] = new Integer( getToken( 0 ).endColumn );
      _vImports.addElement( aoImport );
      _ncss++;
  }

  final public void TypeDeclaration() throws ParseException {
   int modifiers;
    if (jj_2_2(2147483647)) {
      label_9:
      while (true) {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case AT:
          ;
          break;
        default:
          jj_la1[14] = jj_gen;
          break label_9;
        }
        Annotation();
      }
      ClassDeclaration();
    } else if (jj_2_3(2147483647)) {
      modifiers = Modifiers();
      EnumDeclaration(modifiers);
    } else if (jj_2_4(2147483647)) {
      label_10:
      while (true) {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case AT:
          ;
          break;
        default:
          jj_la1[15] = jj_gen;
          break label_10;
        }
        Annotation();
      }
      InterfaceDeclaration();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case FINAL:
      case NATIVE:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case STATIC:
      case TESTAAAA:
      case SYNCHRONIZED:
      case TRANSIENT:
      case VOLATILE:
      case AT:
        modifiers = Modifiers();
        AnnotationTypeDeclaration(modifiers);
        break;
      case SEMICOLON:
        jj_consume_token(SEMICOLON);
        break;
      default:
        jj_la1[16] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

/*
 * Declaration syntax follows.
 */
  final public void ClassDeclaration() throws ParseException {
    Token tmpToken = null;
    _javadocs = 0;
    Vector vMetric = null;

    // added by SMS
    int oldSingle = 0;
    int oldMulti  = 0;

    _jvdcLines    = 0;
    boolean bTemp = _bPublic;
    _bPublic      = false;
    label_11:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case FINAL:
      case PUBLIC:
      case TESTAAAA:
      case SYNCHRONIZED:
        ;
        break;
      default:
        jj_la1[17] = jj_gen;
        break label_11;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
        jj_consume_token(ABSTRACT);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case FINAL:
        jj_consume_token(FINAL);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case PUBLIC:
        jj_consume_token(PUBLIC);
      _bPublic = true;         // added by SMS
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case SYNCHRONIZED:
        jj_consume_token(SYNCHRONIZED);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case TESTAAAA:
        jj_consume_token(TESTAAAA);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      default:
        jj_la1[18] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
      if ( tmpToken == null ) {
          tmpToken = getToken( 1 );
      }
      while( tmpToken.specialToken != null ) {
          if ( tmpToken.specialToken.image.startsWith( "/**" ) ) {
              _javadocs++;
              if (_bPublic || _bPrivate) {
                  _jvdc++;
                  _jvdcLines += token_source._iMultiCommentsLast;
                  token_source._iFormalComments += token_source._iMultiCommentsLast;
              }
              token_source._iMultiComments -= token_source._iMultiCommentsLast;
              break;
          }  else if ( tmpToken.specialToken.image.startsWith( "/*" ) ) {
              break;
          }

          //System.out.println("\n"+tmpToken.specialToken.image);

          tmpToken = tmpToken.specialToken;
      }

      oldSingle = token_source._iSingleComments;
      oldMulti = token_source._iMultiComments;
    UnmodifiedClassDeclaration();
             /* removed by SMS
             while( tmpToken.specialToken != null ) {
                 if ( tmpToken.specialToken.image.startsWith( "/**" ) ) {
                     _javadocs++;
                 }
                 tmpToken = tmpToken.specialToken;
             }
             */
             vMetric = (Vector)_vClasses.lastElement();
             vMetric.addElement( new Integer( _javadocs ) );

             // added by SMS
             vMetric.addElement( new Integer(_jvdcLines));
             vMetric.addElement( new Integer(token_source._iSingleComments - oldSingle));
             vMetric.addElement( new Integer(token_source._iMultiComments - oldMulti));
             //

            // added by SMS
            _bPublic = bTemp;
  }

  final public void UnmodifiedClassDeclaration() throws ParseException {
        String sOldClass = _sClass;
        int oldNcss = _ncss;
        int oldFunctions = _functions;
        int oldClasses = _classes;
                if (!_sClass.equals("")) {
                        _sClass += ".";
                }
                _sClass += new String(getToken(2).image);
                _classLevel ++;
    Modifiers();
    jj_consume_token(CLASS);
    Identifier();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LT:
      TypeParameters();
      break;
    default:
      jj_la1[19] = jj_gen;
      ;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case EXTENDS:
      jj_consume_token(EXTENDS);
      Name();
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LT:
        TypeArguments();
        break;
      default:
        jj_la1[20] = jj_gen;
        ;
      }
      break;
    default:
      jj_la1[21] = jj_gen;
      ;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case IMPLEMENTS:
      jj_consume_token(IMPLEMENTS);
      NameList();
      break;
    default:
      jj_la1[22] = jj_gen;
      ;
    }
    ClassBody();
                _ncss++;
                _classLevel--;
                if (_classLevel == 0) {
                        //_topLevelClasses++;
                        Vector vMetrics = new Vector();
                        vMetrics.addElement(new String(_sPackage + _sClass));
                        vMetrics.addElement(new Integer(_ncss - oldNcss));
                        vMetrics.addElement(new Integer(_functions - oldFunctions));
                        vMetrics.addElement(new Integer(_classes - oldClasses));
                        Token lastToken = getToken( 0 );
                        vMetrics.addElement( new Integer( lastToken.endLine ) );
                        vMetrics.addElement( new Integer( lastToken.endColumn ) );
                        //vMetrics.addElement( new Integer( _javadocs ) );
                        _vClasses.addElement(vMetrics);
                        _pPackageMetric.functions += _functions - oldFunctions;
                        _pPackageMetric.classes++;

                        // added by SMS
                        _pPackageMetric.javadocs += _javadocs;
                        //_pPackageMetric.javadocsLn += token_source._iFormalComments - oldFormal;
                        //_pPackageMetric.singleLn += token_source._iSingleComments - oldSingle;
                        //_pPackageMetric.multiLn += token_source._iMultiComments - oldMulti;
                        //
                }
                _functions = oldFunctions;
                _classes = oldClasses + 1;
                _sClass = sOldClass;
  }

  final public void ClassBody() throws ParseException {
    jj_consume_token(LBRACE);
    label_12:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case ASSERT:
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case CLASS:
      case DOUBLE:
      case ENUM:
      case FINAL:
      case FLOAT:
      case INT:
      case INTERFACE:
      case LONG:
      case NATIVE:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case SHORT:
      case STATIC:
      case TESTAAAA:
      case SYNCHRONIZED:
      case TRANSIENT:
      case VOID:
      case VOLATILE:
      case IDENTIFIER:
      case LBRACE:
      case SEMICOLON:
      case AT:
      case LT:
        ;
        break;
      default:
        jj_la1[23] = jj_gen;
        break label_12;
      }
      ClassBodyDeclaration();
    }
    jj_consume_token(RBRACE);
  }

  final public void NestedClassDeclaration() throws ParseException {
    // added by SMS
    Token tmpToken = null;

    boolean bTemp = _bPublic;
    _bPublic = false;
    boolean bPublic = false;
    label_13:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case FINAL:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case STATIC:
      case TESTAAAA:
        ;
        break;
      default:
        jj_la1[24] = jj_gen;
        break label_13;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case STATIC:
        jj_consume_token(STATIC);
        break;
      case ABSTRACT:
        jj_consume_token(ABSTRACT);
        break;
      case FINAL:
        jj_consume_token(FINAL);
        break;
      case PUBLIC:
        jj_consume_token(PUBLIC);
               bPublic = true;
        break;
      case PROTECTED:
        jj_consume_token(PROTECTED);
                  bPublic = true;
        break;
      case PRIVATE:
        jj_consume_token(PRIVATE);
        break;
      case TESTAAAA:
        jj_consume_token(TESTAAAA);
        break;
      default:
        jj_la1[25] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
                tmpToken = getToken( 0 );

                while( tmpToken.specialToken != null ) {
                    if ( tmpToken.specialToken.image.startsWith( "/**" ) ) {
                        _javadocs++;
                        if ((_bPublic && bPublic) || _bPrivate) {
                            _jvdc++;
                            _jvdcLines += token_source._iMultiCommentsLast;
                            token_source._iFormalComments += token_source._iMultiCommentsLast;
                        }
                        token_source._iMultiComments -= token_source._iMultiCommentsLast;
                        break;
                    }  else if ( tmpToken.specialToken.image.startsWith( "/*" ) ) {
                        break;
                    }

                    //System.out.println("\n"+tmpToken.specialToken.image);

                    tmpToken = tmpToken.specialToken;
                }
    UnmodifiedClassDeclaration();
       //added by SMS
      _bPublic = bTemp;
  }

  final public void ClassBodyDeclaration() throws ParseException {
    int modifiers;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case SEMICOLON:
      EmptyStatement();
      break;
    default:
      jj_la1[29] = jj_gen;
      if (jj_2_5(2)) {
        Initializer();
      } else if (jj_2_6(2147483647)) {
        label_14:
        while (true) {
          switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
          case AT:
            ;
            break;
          default:
            jj_la1[26] = jj_gen;
            break label_14;
          }
          Annotation();
        }
        NestedClassDeclaration();
      } else if (jj_2_7(2147483647)) {
        NestedInterfaceDeclaration();
      } else if (jj_2_8(2147483647)) {
        modifiers = Modifiers();
        EnumDeclaration(modifiers);
      } else if (jj_2_9(2147483647)) {
        label_15:
        while (true) {
          switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
          case AT:
            ;
            break;
          default:
            jj_la1[27] = jj_gen;
            break label_15;
          }
          Annotation();
        }
        ConstructorDeclaration();
      } else if (jj_2_10(2147483647)) {
        MethodDeclaration();
      } else {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case ASSERT:
        case BOOLEAN:
        case BYTE:
        case CHAR:
        case DOUBLE:
        case ENUM:
        case FINAL:
        case FLOAT:
        case INT:
        case LONG:
        case PRIVATE:
        case PROTECTED:
        case PUBLIC:
        case SHORT:
        case STATIC:
        case TRANSIENT:
        case VOLATILE:
        case IDENTIFIER:
        case AT:
          label_16:
          while (true) {
            switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
            case AT:
              ;
              break;
            default:
              jj_la1[28] = jj_gen;
              break label_16;
            }
            Annotation();
          }
          FieldDeclaration();
          break;
        default:
          jj_la1[30] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
      }
    }
  }

// This production is to determine lookahead only.
  final public void MethodDeclarationLookahead() throws ParseException {
    label_17:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AT:
        ;
        break;
      default:
        jj_la1[31] = jj_gen;
        break label_17;
      }
      Annotation();
    }
    label_18:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case FINAL:
      case NATIVE:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case STATIC:
      case TESTAAAA:
      case SYNCHRONIZED:
        ;
        break;
      default:
        jj_la1[32] = jj_gen;
        break label_18;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PUBLIC:
        jj_consume_token(PUBLIC);
        break;
      case PROTECTED:
        jj_consume_token(PROTECTED);
        break;
      case PRIVATE:
        jj_consume_token(PRIVATE);
        break;
      case STATIC:
        jj_consume_token(STATIC);
        break;
      case ABSTRACT:
        jj_consume_token(ABSTRACT);
        break;
      case FINAL:
        jj_consume_token(FINAL);
        break;
      case NATIVE:
        jj_consume_token(NATIVE);
        break;
      case SYNCHRONIZED:
        jj_consume_token(SYNCHRONIZED);
        break;
      case TESTAAAA:
        jj_consume_token(TESTAAAA);
        break;
      default:
        jj_la1[33] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
    label_19:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AT:
        ;
        break;
      default:
        jj_la1[34] = jj_gen;
        break label_19;
      }
      Annotation();
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LT:
      TypeParameters();
      break;
    default:
      jj_la1[35] = jj_gen;
      ;
    }
    ResultType();
    Identifier();
    jj_consume_token(LPAREN);
  }

  final public void InterfaceDeclaration() throws ParseException {
        Token tmpToken = null;
        _javadocs = 0;
        boolean bClassComment = false;
        Vector vMetric = null;

        // added by SMS
        int oldSingle;
        int oldMulti;

        _jvdcLines = 0;
        boolean bTemp = _bPublic;
        _bPublic = false;
    label_20:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case PUBLIC:
      case TESTAAAA:
        ;
        break;
      default:
        jj_la1[36] = jj_gen;
        break label_20;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case TESTAAAA:
        jj_consume_token(TESTAAAA);
        break;
      case ABSTRACT:
        jj_consume_token(ABSTRACT);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case PUBLIC:
        jj_consume_token(PUBLIC);
      _bPublic = true;         // added by SMS
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      default:
        jj_la1[37] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
      if ( tmpToken == null ) {
          tmpToken = getToken( 1 );
      }
                while( tmpToken.specialToken != null ) {
                    if ( tmpToken.specialToken.image.startsWith( "/**" ) ) {
                        _javadocs++;
                        if (_bPublic || _bPrivate) {
                            _jvdc++;
                            _jvdcLines += token_source._iMultiCommentsLast;
                            token_source._iFormalComments += token_source._iMultiCommentsLast;
                        }
                        token_source._iMultiComments -= token_source._iMultiCommentsLast;
                        break;
                    }  else if ( tmpToken.specialToken.image.startsWith( "/*" ) ) {
                        break;
                    }

                    //System.out.println("\n"+tmpToken.specialToken.image);

                    tmpToken = tmpToken.specialToken;
                }

                oldSingle = token_source._iSingleComments;
                oldMulti = token_source._iMultiComments;
    UnmodifiedInterfaceDeclaration();
             /* removed by SMS
             while( tmpToken.specialToken != null ) {
                 if ( tmpToken.specialToken.image.startsWith( "/**" ) ) {
                     _javadocs++;
                     bClassComment = true;
                 }
                 tmpToken = tmpToken.specialToken;
                 }*/
             vMetric = (Vector)_vClasses.lastElement();
             vMetric.addElement( new Integer( _javadocs ) );

             // added by SMS
             vMetric.addElement( new Integer(_jvdcLines));
             vMetric.addElement( new Integer(token_source._iSingleComments - oldSingle));
             vMetric.addElement( new Integer(token_source._iMultiComments - oldMulti));
             //

            // added by SMS
            _bPublic = bTemp;
  }

  final public void NestedInterfaceDeclaration() throws ParseException {
    // added by SMS
    Token tmpToken = null;

    boolean bTemp = _bPublic;
    _bPublic = false;
    boolean bPublic = false;
    label_21:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case FINAL:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case STATIC:
      case TESTAAAA:
        ;
        break;
      default:
        jj_la1[38] = jj_gen;
        break label_21;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case STATIC:
        jj_consume_token(STATIC);
        break;
      case ABSTRACT:
        jj_consume_token(ABSTRACT);
        break;
      case FINAL:
        jj_consume_token(FINAL);
        break;
      case PUBLIC:
        jj_consume_token(PUBLIC);
               bPublic = true;
        break;
      case PROTECTED:
        jj_consume_token(PROTECTED);
                  bPublic = true;
        break;
      case PRIVATE:
        jj_consume_token(PRIVATE);
        break;
      case TESTAAAA:
        jj_consume_token(TESTAAAA);
        break;
      default:
        jj_la1[39] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
                tmpToken = getToken( 0 );

                while( tmpToken.specialToken != null ) {
                    if ( tmpToken.specialToken.image.startsWith( "/**" ) ) {
                        _javadocs++;
                        if ((_bPublic && bPublic) || _bPrivate) {
                            _jvdc++;
                            _jvdcLines += token_source._iMultiCommentsLast;
                            token_source._iFormalComments += token_source._iMultiCommentsLast;
                        }
                        token_source._iMultiComments -= token_source._iMultiCommentsLast;
                        break;
                    }  else if ( tmpToken.specialToken.image.startsWith( "/*" ) ) {
                        break;
                    }

                    //System.out.println("\n"+tmpToken.specialToken.image);

                    tmpToken = tmpToken.specialToken;
                }
    UnmodifiedInterfaceDeclaration();
      // added by SMS
      _bPublic = bTemp;
  }

  final public void UnmodifiedInterfaceDeclaration() throws ParseException {
        String sOldClass = _sClass;
        int oldNcss = _ncss;
        int oldFunctions = _functions;
        int oldClasses = _classes;
                if (!_sClass.equals("")) {
                        _sClass += ".";
                }
                _sClass += new String(getToken(2).image);
                _classLevel ++;
    jj_consume_token(INTERFACE);
    Identifier();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LT:
      TypeParameters();
      break;
    default:
      jj_la1[40] = jj_gen;
      ;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case EXTENDS:
      jj_consume_token(EXTENDS);
      NameList();
      break;
    default:
      jj_la1[41] = jj_gen;
      ;
    }
    jj_consume_token(LBRACE);
    label_22:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case ASSERT:
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case CLASS:
      case DOUBLE:
      case ENUM:
      case FINAL:
      case FLOAT:
      case INT:
      case INTERFACE:
      case LONG:
      case NATIVE:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case SHORT:
      case STATIC:
      case TESTAAAA:
      case SYNCHRONIZED:
      case TRANSIENT:
      case VOID:
      case VOLATILE:
      case IDENTIFIER:
      case SEMICOLON:
      case AT:
      case LT:
        ;
        break;
      default:
        jj_la1[42] = jj_gen;
        break label_22;
      }
      InterfaceMemberDeclaration();
    }
    jj_consume_token(RBRACE);
                _ncss++;
                _classLevel--;
                if (_classLevel == 0)
                {
                        //_topLevelClasses++;
                        Vector vMetrics = new Vector();
                        vMetrics.addElement(new String(_sPackage + _sClass));
                        vMetrics.addElement(new Integer(_ncss - oldNcss));
                        vMetrics.addElement(new Integer(_functions - oldFunctions));
                        vMetrics.addElement(new Integer(_classes - oldClasses));
                        vMetrics.addElement( "" );
                        vMetrics.addElement( "" );
                        _vClasses.addElement(vMetrics);
                        _pPackageMetric.functions += _functions - oldFunctions;
                        _pPackageMetric.classes++;

                        // added by SMS
                        _pPackageMetric.javadocs += _javadocs;
                        //_pPackageMetric.javadocsLn += token_source._iFormalComments - oldFormal;
                        //_pPackageMetric.singleLn += token_source._iSingleComments - oldSingle;
                        //_pPackageMetric.multiLn += token_source._iMultiComments - oldMulti;
                        //
                }
                _functions = oldFunctions;
                _classes = oldClasses + 1;
                _sClass = sOldClass;
  }

  final public void InterfaceMemberDeclaration() throws ParseException {
   int modifiers;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case SEMICOLON:
      EmptyStatement();
      break;
    default:
      jj_la1[43] = jj_gen;
      if (jj_2_11(2147483647)) {
        NestedClassDeclaration();
      } else if (jj_2_12(2147483647)) {
        NestedInterfaceDeclaration();
      } else if (jj_2_13(2147483647)) {
        modifiers = Modifiers();
        EnumDeclaration(modifiers);
      } else if (jj_2_14(2147483647)) {
        MethodDeclaration();
      } else {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case ASSERT:
        case BOOLEAN:
        case BYTE:
        case CHAR:
        case DOUBLE:
        case ENUM:
        case FINAL:
        case FLOAT:
        case INT:
        case LONG:
        case PRIVATE:
        case PROTECTED:
        case PUBLIC:
        case SHORT:
        case STATIC:
        case TRANSIENT:
        case VOLATILE:
        case IDENTIFIER:
        case AT:
          FieldDeclaration();
          break;
        default:
          jj_la1[44] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
      }
    }
  }

  final public void FieldDeclaration() throws ParseException {
    // added by SMS
    Token tmpToken = null;
    boolean bPublic = false;
    label_23:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case FINAL:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case STATIC:
      case TRANSIENT:
      case VOLATILE:
        ;
        break;
      default:
        jj_la1[45] = jj_gen;
        break label_23;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PUBLIC:
        jj_consume_token(PUBLIC);
               bPublic = true;
        break;
      case PROTECTED:
        jj_consume_token(PROTECTED);
                  bPublic = true;
        break;
      case PRIVATE:
        jj_consume_token(PRIVATE);
        break;
      case STATIC:
        jj_consume_token(STATIC);
        break;
      case FINAL:
        jj_consume_token(FINAL);
        break;
      case TRANSIENT:
        jj_consume_token(TRANSIENT);
        break;
      case VOLATILE:
        jj_consume_token(VOLATILE);
        break;
      default:
        jj_la1[46] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
                tmpToken = getToken( 0 );

                while( tmpToken.specialToken != null )
                {
                    if ( tmpToken.specialToken.image.startsWith( "/**" ) )
                    {
                        if ((bPublic && _bPublic) || _bPrivate)
                        {
                            //_javadocs++;
                            _jvdc++;
                            _jvdcLines += token_source._iMultiCommentsLast;
                            token_source._iFormalComments += token_source._iMultiCommentsLast;
                        }
                        token_source._iMultiComments -= token_source._iMultiCommentsLast;
                        break;
                    }
                    else if ( tmpToken.specialToken.image.startsWith( "/*" ) )
                    {
                        break;
                    }

                    //System.out.println("\n"+tmpToken.specialToken.image);

                    tmpToken = tmpToken.specialToken;
                }
    label_24:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AT:
        ;
        break;
      default:
        jj_la1[47] = jj_gen;
        break label_24;
      }
      Annotation();
    }
    Type();
    VariableDeclarator();
    label_25:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case COMMA:
        ;
        break;
      default:
        jj_la1[48] = jj_gen;
        break label_25;
      }
      jj_consume_token(COMMA);
      VariableDeclarator();
    }
    jj_consume_token(SEMICOLON);
    _ncss++;
  }

  final public void VariableDeclarator() throws ParseException {
    VariableDeclaratorId();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ASSIGN:
      jj_consume_token(ASSIGN);
      VariableInitializer();
      break;
    default:
      jj_la1[49] = jj_gen;
      ;
    }
  }

  final public void VariableDeclaratorId() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ENUM:
      jj_consume_token(ENUM);
      break;
    case ASSERT:
    case IDENTIFIER:
      Identifier();
      break;
    default:
      jj_la1[50] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    label_26:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LBRACKET:
        ;
        break;
      default:
        jj_la1[51] = jj_gen;
        break label_26;
      }
      jj_consume_token(LBRACKET);
      jj_consume_token(RBRACKET);
                                          _sName += "[]";
    }
  }

  final public void VariableInitializer() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LBRACE:
      ArrayInitializer();
      break;
    case ASSERT:
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case ENUM:
    case FALSE:
    case FLOAT:
    case INT:
    case LONG:
    case NEW:
    case NULL:
    case SHORT:
    case SUPER:
    case THIS:
    case TRUE:
    case VOID:
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case CHARACTER_LITERAL:
    case STRING_LITERAL:
    case IDENTIFIER:
    case LPAREN:
    case BANG:
    case TILDE:
    case INCR:
    case DECR:
    case PLUS:
    case MINUS:
      Expression();
      break;
    default:
      jj_la1[52] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void ArrayInitializer() throws ParseException {
    jj_consume_token(LBRACE);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ASSERT:
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case ENUM:
    case FALSE:
    case FLOAT:
    case INT:
    case LONG:
    case NEW:
    case NULL:
    case SHORT:
    case SUPER:
    case THIS:
    case TRUE:
    case VOID:
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case CHARACTER_LITERAL:
    case STRING_LITERAL:
    case IDENTIFIER:
    case LPAREN:
    case LBRACE:
    case BANG:
    case TILDE:
    case INCR:
    case DECR:
    case PLUS:
    case MINUS:
      VariableInitializer();
      label_27:
      while (true) {
        if (jj_2_15(2)) {
          ;
        } else {
          break label_27;
        }
        jj_consume_token(COMMA);
        VariableInitializer();
      }
      break;
    default:
      jj_la1[53] = jj_gen;
      ;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case COMMA:
      jj_consume_token(COMMA);
      break;
    default:
      jj_la1[54] = jj_gen;
      ;
    }
    jj_consume_token(RBRACE);
  }

  final public void MethodDeclaration() throws ParseException {
    int oldNcss = _ncss;
    int oldFunctions = _functions;
    String sOldFunction = _sFunction;
    int oldcyc = _cyc;
    boolean bOldReturn = _bReturn;
    Token tmpToken = null;
    int jvdc = 0;
    int beginLine = 0;
    int endLine = 0;

    // added by SMS
    int jvdcLines = 0;
    int oldSingle;
    int oldMulti;
    boolean bPublic = false;
    if ( _tmpToken != null )
    {
        tmpToken = _tmpToken;
    }
    label_28:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AT:
        ;
        break;
      default:
        jj_la1[55] = jj_gen;
        break label_28;
      }
      Annotation();
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
    }
    label_29:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case FINAL:
      case NATIVE:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case STATIC:
      case TESTAAAA:
      case SYNCHRONIZED:
        ;
        break;
      default:
        jj_la1[56] = jj_gen;
        break label_29;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PUBLIC:
        jj_consume_token(PUBLIC);
               bPublic = true;
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case PROTECTED:
        jj_consume_token(PROTECTED);
                    bPublic = true;
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case PRIVATE:
        jj_consume_token(PRIVATE);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case STATIC:
        jj_consume_token(STATIC);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case ABSTRACT:
        jj_consume_token(ABSTRACT);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case FINAL:
        jj_consume_token(FINAL);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case NATIVE:
        jj_consume_token(NATIVE);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case SYNCHRONIZED:
        jj_consume_token(SYNCHRONIZED);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case TESTAAAA:
        jj_consume_token(TESTAAAA);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      default:
        jj_la1[57] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
    label_30:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AT:
        ;
        break;
      default:
        jj_la1[58] = jj_gen;
        break label_30;
      }
      Annotation();
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LT:
      TypeParameters();
      break;
    default:
      jj_la1[59] = jj_gen;
      ;
    }
               _tmpResultToken = null;
    ResultType();
            if ( tmpToken == null )
            {
                tmpToken = _tmpResultToken;
                if ( tmpToken == null )
                {
                    tmpToken = getToken( 0 );
                }
            }
    MethodDeclarator();
    beginLine = token.beginLine;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case THROWS:
      jj_consume_token(THROWS);
      NameList();
      break;
    default:
      jj_la1[60] = jj_gen;
      ;
    }
                _cyc = 1;
                _bReturn = false;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LBRACE:
      Block();
      endLine = token.endLine;
      break;
    case SEMICOLON:
      jj_consume_token(SEMICOLON);
      break;
    default:
      jj_la1[61] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  // added by SMS
  {
      while( tmpToken.specialToken != null )
      {
          if ( tmpToken.specialToken.image.startsWith( "/**" ) )
          {
              _javadocs++;
              jvdc++;
              if ((bPublic && _bPublic) || _bPrivate) {
                  _jvdc++;
                  jvdcLines = token_source._iMultiCommentsLast;
                  _jvdcLines += jvdcLines;
                  token_source._iFormalComments += jvdcLines;
              }
              token_source._iMultiComments -= jvdcLines;
              break;
          }  else if ( tmpToken.specialToken.image.startsWith( "/*" ) ) {
              jvdcLines = 0;
              break;
          }

          //System.out.println("\n"+tmpToken.specialToken.image);

          tmpToken = tmpToken.specialToken;
      }

      oldSingle = token_source._iSingleComments;
      oldMulti = token_source._iMultiComments;
  }


             // removed by ccl 
             /*
             while( tmpToken.specialToken != null ) {
                 if ( tmpToken.specialToken.image.startsWith( "/**" ) ) {
                     jvdc++;
                     _javadocs++;
                 }
                 tmpToken = tmpToken.specialToken;
             }
             */
             // removed by SMS
             /*
               while( tmpToken.specialToken != null ) {
               if ( tmpToken.specialToken.image.startsWith( "/**" ) ) {
               jvdc++;
               _javadocs++; 
               _bJavadoc = true;
               }
               
               tmpToken = tmpToken.specialToken;
               }
             */

             if (_bReturn)
             {
                 _cyc--;
             }
             _ncss++;

             Vector vFunctionMetrics = new Vector();
             vFunctionMetrics.addElement(new String(_sPackage + _sClass +
                                                    _sFunction));
             vFunctionMetrics.addElement(new Integer(_ncss - oldNcss));
             vFunctionMetrics.addElement(new Integer(_cyc));
             vFunctionMetrics.addElement( new Integer(jvdc) );

             //* added by SMS
             vFunctionMetrics.addElement( new Integer( 0 ) );//jvdcLines));
             vFunctionMetrics.addElement( new Integer( 0 ) );//token_source._iSingleComments - oldSingle));
             vFunctionMetrics.addElement( new Integer( 0 ) );//token_source._iMultiComments - oldMulti));
             // */
             
             // specially added for Cobertura
             vFunctionMetrics.add(new Integer(beginLine));
             vFunctionMetrics.add(new Integer(endLine));

             _vFunctions.addElement(vFunctionMetrics);
             _sFunction = sOldFunction;
             _functions = oldFunctions + 1;
             _cyc = oldcyc;
             _bReturn = bOldReturn;
  }

  final public void MethodDeclarator() throws ParseException {
                _sFunction = "." + new String(getToken(1).image);
    Identifier();
    FormalParameters();
                _sFunction += _sParameter;
    label_31:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LBRACKET:
        ;
        break;
      default:
        jj_la1[62] = jj_gen;
        break label_31;
      }
      jj_consume_token(LBRACKET);
      jj_consume_token(RBRACKET);
              _sFunction += "[]";
    }
  }

  final public void FormalParameters() throws ParseException {
                _sParameter = "(";
    jj_consume_token(LPAREN);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ABSTRACT:
    case ASSERT:
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case ENUM:
    case FINAL:
    case FLOAT:
    case INT:
    case LONG:
    case NATIVE:
    case PRIVATE:
    case PROTECTED:
    case PUBLIC:
    case SHORT:
    case STATIC:
    case TESTAAAA:
    case SYNCHRONIZED:
    case TRANSIENT:
    case VOLATILE:
    case IDENTIFIER:
    case AT:
      FormalParameter();
                            _sParameter += _sName;
      label_32:
      while (true) {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case COMMA:
          ;
          break;
        default:
          jj_la1[63] = jj_gen;
          break label_32;
        }
        jj_consume_token(COMMA);
        FormalParameter();
                            _sParameter += "," + _sName;
      }
      break;
    default:
      jj_la1[64] = jj_gen;
      ;
    }
    jj_consume_token(RPAREN);
                _sParameter += ")";
  }

  final public void FormalParameter() throws ParseException {
    Modifiers();
    Type();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ELLIPSIS:
      jj_consume_token(ELLIPSIS);
      break;
    default:
      jj_la1[65] = jj_gen;
      ;
    }
    VariableDeclaratorId();
  }

  final public void ConstructorDeclaration() throws ParseException {
        int oldNcss = _ncss;
        int oldFunctions = _functions;
        String sOldFunction = _sFunction;
        int oldcyc = _cyc;
        boolean bOldReturn = _bReturn;
        Token tmpToken = null;
        int jvdc = 0;

        int beginLine = 0;
        int endLine = 0;

        // added by SMS
        int oldSingle;
        int oldMulti;
        int jvdcLines = 0;
        boolean bPublic = false;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case PRIVATE:
    case PROTECTED:
    case PUBLIC:
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PUBLIC:
        jj_consume_token(PUBLIC);
               bPublic = true;
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case PROTECTED:
        jj_consume_token(PROTECTED);
                 bPublic = true;
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      case PRIVATE:
        jj_consume_token(PRIVATE);
      if ( tmpToken == null ) {
          tmpToken = getToken( 0 );
      }
        break;
      default:
        jj_la1[66] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      break;
    default:
      jj_la1[67] = jj_gen;
      ;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LT:
      TypeParameters();
      break;
    default:
      jj_la1[68] = jj_gen;
      ;
    }
    Identifier();
    beginLine = token.beginLine;
            if ( tmpToken == null ) {
                tmpToken = getToken( 0 );
            }
                _cyc = 1;
                _sFunction = _sPackage + _sClass + "." + getToken(0).image;
    FormalParameters();
                _sFunction += _sParameter;
                _bReturn = false;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case THROWS:
      jj_consume_token(THROWS);
      NameList();
      break;
    default:
      jj_la1[69] = jj_gen;
      ;
    }
    jj_consume_token(LBRACE);
    if (jj_2_16(2147483647)) {
      ExplicitConstructorInvocation();
    } else {
      ;
    }
    if (jj_2_17(2147483647)) {
      ExplicitConstructorInvocation();
    } else {
      ;
    }
                while( tmpToken.specialToken != null ) {
                    if ( tmpToken.specialToken.image.startsWith( "/**" ) ) {
                        _javadocs++;
                        jvdc++;
                        if ((bPublic && _bPublic) || _bPrivate) {
                            _jvdc++;
                            jvdcLines = token_source._iMultiCommentsLast;
                            _jvdcLines += jvdcLines;
                            token_source._iFormalComments += jvdcLines;
                        }
                        token_source._iMultiComments -= jvdcLines;
                        break;
                    }  else if ( tmpToken.specialToken.image.startsWith( "/*" ) ) {
                        jvdcLines = 0;
                        break;
                    }

                    //System.out.println("\n"+tmpToken.specialToken.image);

                    tmpToken = tmpToken.specialToken;
                }


                oldSingle = token_source._iSingleComments;
                oldMulti = token_source._iMultiComments;
    label_33:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case ASSERT:
      case BOOLEAN:
      case BREAK:
      case BYTE:
      case CHAR:
      case CLASS:
      case CONTINUE:
      case DO:
      case DOUBLE:
      case ENUM:
      case FALSE:
      case FINAL:
      case FLOAT:
      case FOR:
      case IF:
      case INT:
      case INTERFACE:
      case LONG:
      case NATIVE:
      case NEW:
      case NULL:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case RETURN:
      case SHORT:
      case STATIC:
      case TESTAAAA:
      case SUPER:
      case SWITCH:
      case SYNCHRONIZED:
      case THIS:
      case THROW:
      case TRANSIENT:
      case TRUE:
      case TRY:
      case VOID:
      case VOLATILE:
      case WHILE:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case CHARACTER_LITERAL:
      case STRING_LITERAL:
      case IDENTIFIER:
      case LPAREN:
      case LBRACE:
      case SEMICOLON:
      case AT:
      case INCR:
      case DECR:
        ;
        break;
      default:
        jj_la1[70] = jj_gen;
        break label_33;
      }
      BlockStatement();
    }
    jj_consume_token(RBRACE);
    endLine = token.endLine;
            /*
                while( tmpToken.specialToken != null ) {
                    if ( tmpToken.specialToken.image.startsWith( "/**" ) ) {
                        jvdc++;
                        _javadocs++;
                    }
                    tmpToken = tmpToken.specialToken;
                }
            */
                if (_bReturn) {
                        _cyc--;
                }
                _ncss++;

                Vector vFunctionMetrics = new Vector();
                vFunctionMetrics.addElement(new String(_sFunction));
                vFunctionMetrics.addElement(new Integer(_ncss - oldNcss));
                vFunctionMetrics.addElement(new Integer(_cyc));
                vFunctionMetrics.addElement( new Integer(jvdc) );

                // added by SMS
                vFunctionMetrics.addElement( new Integer(jvdcLines));
                vFunctionMetrics.addElement( new Integer(token_source._iSingleComments - oldSingle));
                vFunctionMetrics.addElement( new Integer(token_source._iMultiComments - oldMulti));
                //
                // specially added for Cobertura
                vFunctionMetrics.add(new Integer(beginLine));
                vFunctionMetrics.add(new Integer(endLine));

                _vFunctions.addElement(vFunctionMetrics);
                _sFunction = sOldFunction;
                _functions = oldFunctions + 1;
                _cyc = oldcyc;
                _bReturn = bOldReturn;
  }

  final public void ExplicitConstructorInvocation() throws ParseException {
    if (jj_2_19(2147483647)) {
      jj_consume_token(THIS);
      Arguments();
      jj_consume_token(SEMICOLON);
           _ncss++;       
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ASSERT:
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case DOUBLE:
      case ENUM:
      case FALSE:
      case FLOAT:
      case INT:
      case LONG:
      case NEW:
      case NULL:
      case SHORT:
      case SUPER:
      case THIS:
      case TRUE:
      case VOID:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case CHARACTER_LITERAL:
      case STRING_LITERAL:
      case IDENTIFIER:
      case LPAREN:
        if (jj_2_18(2147483647)) {
          PrimaryExpression();
          jj_consume_token(DOT);
        } else {
          ;
        }
        jj_consume_token(SUPER);
        Arguments();
        jj_consume_token(SEMICOLON);
    _ncss++;       
//System.out.println( "\n\nAfter ExplicitConstructorInvocation\n" ); 

        break;
      default:
        jj_la1[71] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

  final public void Initializer() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case STATIC:
      jj_consume_token(STATIC);
      break;
    default:
      jj_la1[72] = jj_gen;
      ;
    }
    Block();
          _ncss++;       
  }

/*
 * Type, name and expression syntax follows.
 */
  final public void Type() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case FLOAT:
    case INT:
    case LONG:
    case SHORT:
      PrimitiveType();
                  _sName = (new String(getToken(0).image));
      break;
    case ASSERT:
    case ENUM:
    case IDENTIFIER:
      Name();
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LT:
        TypeArguments();
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case DOT:
          jj_consume_token(DOT);
          Identifier();
          break;
        default:
          jj_la1[73] = jj_gen;
          ;
        }
        break;
      default:
        jj_la1[74] = jj_gen;
        ;
      }
      break;
    default:
      jj_la1[75] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    label_34:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LBRACKET:
        ;
        break;
      default:
        jj_la1[76] = jj_gen;
        break label_34;
      }
      jj_consume_token(LBRACKET);
      jj_consume_token(RBRACKET);
              _sName += "[]";
    }
  }

/*
 * Takes special consideration for assert.
 */
  final public void FieldTypeLookahead() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case FLOAT:
    case INT:
    case LONG:
    case SHORT:
      PrimitiveType();
      break;
    case IDENTIFIER:
      FieldTypeNameLookahead();
      break;
    default:
      jj_la1[77] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    label_35:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LBRACKET:
        ;
        break;
      default:
        jj_la1[78] = jj_gen;
        break label_35;
      }
      jj_consume_token(LBRACKET);
      jj_consume_token(RBRACKET);
    }
  }

  final public void PrimitiveType() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case BOOLEAN:
      jj_consume_token(BOOLEAN);
      break;
    case CHAR:
      jj_consume_token(CHAR);
      break;
    case BYTE:
      jj_consume_token(BYTE);
      break;
    case SHORT:
      jj_consume_token(SHORT);
      break;
    case INT:
      jj_consume_token(INT);
      break;
    case LONG:
      jj_consume_token(LONG);
      break;
    case FLOAT:
      jj_consume_token(FLOAT);
      break;
    case DOUBLE:
      jj_consume_token(DOUBLE);
      break;
    default:
      jj_la1[79] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void ResultType() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case VOID:
      jj_consume_token(VOID);
      break;
    case ASSERT:
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case ENUM:
    case FLOAT:
    case INT:
    case LONG:
    case SHORT:
    case IDENTIFIER:
      Type();
      break;
    default:
      jj_la1[80] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void Name() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ENUM:
      jj_consume_token(ENUM);
      break;
    case ASSERT:
    case IDENTIFIER:
      Identifier();
      break;
    default:
      jj_la1[81] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
                _sName = new String(getToken(0).image);
                _tmpResultToken = getToken( 0 );
    label_36:
    while (true) {
      if (jj_2_20(2)) {
        ;
      } else {
        break label_36;
      }
      jj_consume_token(DOT);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ENUM:
        jj_consume_token(ENUM);
        break;
      case ASSERT:
      case IDENTIFIER:
        Identifier();
        break;
      default:
        jj_la1[82] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
                _sName += "." + (new String(getToken(0).image));
    }
  }

/**
 * Takes special consideration for assert.
 */
  final public void FieldTypeNameLookahead() throws ParseException {
    jj_consume_token(IDENTIFIER);
    label_37:
    while (true) {
      if (jj_2_21(2)) {
        ;
      } else {
        break label_37;
      }
      jj_consume_token(DOT);
      Identifier();
    }
  }

  final public void NameList() throws ParseException {
    Name();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LT:
      TypeArguments();
      break;
    default:
      jj_la1[83] = jj_gen;
      ;
    }
    label_38:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case COMMA:
        ;
        break;
      default:
        jj_la1[84] = jj_gen;
        break label_38;
      }
      jj_consume_token(COMMA);
      Name();
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LT:
        TypeArguments();
        break;
      default:
        jj_la1[85] = jj_gen;
        ;
      }
    }
  }

/*
 * Expression syntax follows.
 */
  final public void Expression() throws ParseException {
    if (jj_2_22(2147483647)) {
      Assignment();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ASSERT:
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case DOUBLE:
      case ENUM:
      case FALSE:
      case FLOAT:
      case INT:
      case LONG:
      case NEW:
      case NULL:
      case SHORT:
      case SUPER:
      case THIS:
      case TRUE:
      case VOID:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case CHARACTER_LITERAL:
      case STRING_LITERAL:
      case IDENTIFIER:
      case LPAREN:
      case BANG:
      case TILDE:
      case INCR:
      case DECR:
      case PLUS:
      case MINUS:
        ConditionalExpression();
        break;
      default:
        jj_la1[86] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

  final public void Assignment() throws ParseException {
    PrimaryExpression();
    AssignmentOperator();
    Expression();
  }

  final public void AssignmentOperator() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ASSIGN:
      jj_consume_token(ASSIGN);
      break;
    case STARASSIGN:
      jj_consume_token(STARASSIGN);
      break;
    case SLASHASSIGN:
      jj_consume_token(SLASHASSIGN);
      break;
    case REMASSIGN:
      jj_consume_token(REMASSIGN);
      break;
    case PLUSASSIGN:
      jj_consume_token(PLUSASSIGN);
      break;
    case MINUSASSIGN:
      jj_consume_token(MINUSASSIGN);
      break;
    case LSHIFTASSIGN:
      jj_consume_token(LSHIFTASSIGN);
      break;
    case RSIGNEDSHIFTASSIGN:
      jj_consume_token(RSIGNEDSHIFTASSIGN);
      break;
    case RUNSIGNEDSHIFTASSIGN:
      jj_consume_token(RUNSIGNEDSHIFTASSIGN);
      break;
    case ANDASSIGN:
      jj_consume_token(ANDASSIGN);
      break;
    case XORASSIGN:
      jj_consume_token(XORASSIGN);
      break;
    case ORASSIGN:
      jj_consume_token(ORASSIGN);
      break;
    default:
      jj_la1[87] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void ConditionalExpression() throws ParseException {
    ConditionalOrExpression();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case HOOK:
      jj_consume_token(HOOK);
      Expression();
      jj_consume_token(COLON);
      ConditionalExpression();
                                                                             _cyc++;
      break;
    default:
      jj_la1[88] = jj_gen;
      ;
    }
  }

  final public void ConditionalOrExpression() throws ParseException {
    ConditionalAndExpression();
    label_39:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case SC_OR:
        ;
        break;
      default:
        jj_la1[89] = jj_gen;
        break label_39;
      }
      jj_consume_token(SC_OR);
                                      _cyc++;
      ConditionalAndExpression();
    }
  }

  final public void ConditionalAndExpression() throws ParseException {
    InclusiveOrExpression();
    label_40:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case SC_AND:
        ;
        break;
      default:
        jj_la1[90] = jj_gen;
        break label_40;
      }
      jj_consume_token(SC_AND);
                                   _cyc++;
      InclusiveOrExpression();
    }
  }

  final public void InclusiveOrExpression() throws ParseException {
    ExclusiveOrExpression();
    label_41:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case BIT_OR:
        ;
        break;
      default:
        jj_la1[91] = jj_gen;
        break label_41;
      }
      jj_consume_token(BIT_OR);
      ExclusiveOrExpression();
    }
  }

  final public void ExclusiveOrExpression() throws ParseException {
    AndExpression();
    label_42:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case XOR:
        ;
        break;
      default:
        jj_la1[92] = jj_gen;
        break label_42;
      }
      jj_consume_token(XOR);
      AndExpression();
    }
  }

  final public void AndExpression() throws ParseException {
    EqualityExpression();
    label_43:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case BIT_AND:
        ;
        break;
      default:
        jj_la1[93] = jj_gen;
        break label_43;
      }
      jj_consume_token(BIT_AND);
      EqualityExpression();
    }
  }

  final public void EqualityExpression() throws ParseException {
    InstanceOfExpression();
    label_44:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case EQ:
      case NE:
        ;
        break;
      default:
        jj_la1[94] = jj_gen;
        break label_44;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case EQ:
        jj_consume_token(EQ);
        break;
      case NE:
        jj_consume_token(NE);
        break;
      default:
        jj_la1[95] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      InstanceOfExpression();
    }
  }

  final public void InstanceOfExpression() throws ParseException {
    RelationalExpression();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case INSTANCEOF:
      jj_consume_token(INSTANCEOF);
      Type();
      break;
    default:
      jj_la1[96] = jj_gen;
      ;
    }
  }

  final public void RelationalExpression() throws ParseException {
    ShiftExpression();
    label_45:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case GT:
      case LT:
      case LE:
      case GE:
        ;
        break;
      default:
        jj_la1[97] = jj_gen;
        break label_45;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LT:
        jj_consume_token(LT);
        break;
      case GT:
        jj_consume_token(GT);
        break;
      case LE:
        jj_consume_token(LE);
        break;
      case GE:
        jj_consume_token(GE);
        break;
      default:
        jj_la1[98] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      ShiftExpression();
    }
  }

  final public void ShiftExpression() throws ParseException {
    AdditiveExpression();
    label_46:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LSHIFT:
      case RSIGNEDSHIFT:
      case RUNSIGNEDSHIFT:
        ;
        break;
      default:
        jj_la1[99] = jj_gen;
        break label_46;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LSHIFT:
        jj_consume_token(LSHIFT);
        break;
      case RSIGNEDSHIFT:
        jj_consume_token(RSIGNEDSHIFT);
        break;
      case RUNSIGNEDSHIFT:
        jj_consume_token(RUNSIGNEDSHIFT);
        break;
      default:
        jj_la1[100] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      AdditiveExpression();
    }
  }

  final public void AdditiveExpression() throws ParseException {
    MultiplicativeExpression();
    label_47:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PLUS:
      case MINUS:
        ;
        break;
      default:
        jj_la1[101] = jj_gen;
        break label_47;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PLUS:
        jj_consume_token(PLUS);
        break;
      case MINUS:
        jj_consume_token(MINUS);
        break;
      default:
        jj_la1[102] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      MultiplicativeExpression();
    }
  }

  final public void MultiplicativeExpression() throws ParseException {
    UnaryExpression();
    label_48:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case STAR:
      case SLASH:
      case REM:
        ;
        break;
      default:
        jj_la1[103] = jj_gen;
        break label_48;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case STAR:
        jj_consume_token(STAR);
        break;
      case SLASH:
        jj_consume_token(SLASH);
        break;
      case REM:
        jj_consume_token(REM);
        break;
      default:
        jj_la1[104] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      UnaryExpression();
    }
  }

  final public void UnaryExpression() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case PLUS:
    case MINUS:
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PLUS:
        jj_consume_token(PLUS);
        break;
      case MINUS:
        jj_consume_token(MINUS);
        break;
      default:
        jj_la1[105] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      UnaryExpression();
      break;
    case INCR:
      PreIncrementExpression();
      break;
    case DECR:
      PreDecrementExpression();
      break;
    case ASSERT:
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case ENUM:
    case FALSE:
    case FLOAT:
    case INT:
    case LONG:
    case NEW:
    case NULL:
    case SHORT:
    case SUPER:
    case THIS:
    case TRUE:
    case VOID:
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case CHARACTER_LITERAL:
    case STRING_LITERAL:
    case IDENTIFIER:
    case LPAREN:
    case BANG:
    case TILDE:
      UnaryExpressionNotPlusMinus();
      break;
    default:
      jj_la1[106] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void PreIncrementExpression() throws ParseException {
    jj_consume_token(INCR);
    PrimaryExpression();
  }

  final public void PreDecrementExpression() throws ParseException {
    jj_consume_token(DECR);
    PrimaryExpression();
  }

  final public void UnaryExpressionNotPlusMinus() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case BANG:
    case TILDE:
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case TILDE:
        jj_consume_token(TILDE);
        break;
      case BANG:
        jj_consume_token(BANG);
        break;
      default:
        jj_la1[107] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      UnaryExpression();
      break;
    default:
      jj_la1[108] = jj_gen;
      if (jj_2_23(2147483647)) {
        CastExpression();
      } else {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case ASSERT:
        case BOOLEAN:
        case BYTE:
        case CHAR:
        case DOUBLE:
        case ENUM:
        case FALSE:
        case FLOAT:
        case INT:
        case LONG:
        case NEW:
        case NULL:
        case SHORT:
        case SUPER:
        case THIS:
        case TRUE:
        case VOID:
        case INTEGER_LITERAL:
        case FLOATING_POINT_LITERAL:
        case CHARACTER_LITERAL:
        case STRING_LITERAL:
        case IDENTIFIER:
        case LPAREN:
          PostfixExpression();
          break;
        default:
          jj_la1[109] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
      }
    }
  }

// This production is to determine lookahead only.  The LOOKAHEAD specifications
// below are not used, but they are there just to indicate that we know about
// this.
  final public void CastLookahead() throws ParseException {
    if (jj_2_24(2)) {
      jj_consume_token(LPAREN);
      PrimitiveType();
    } else if (jj_2_25(2147483647)) {
      jj_consume_token(LPAREN);
      Type();
      jj_consume_token(LBRACKET);
      jj_consume_token(RBRACKET);
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LPAREN:
        jj_consume_token(LPAREN);
        Type();
        jj_consume_token(RPAREN);
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case TILDE:
          jj_consume_token(TILDE);
          break;
        case BANG:
          jj_consume_token(BANG);
          break;
        case LPAREN:
          jj_consume_token(LPAREN);
          break;
        case ASSERT:
        case IDENTIFIER:
          Identifier();
          break;
        case THIS:
          jj_consume_token(THIS);
          break;
        case SUPER:
          jj_consume_token(SUPER);
          break;
        case NEW:
          jj_consume_token(NEW);
          break;
        case FALSE:
        case NULL:
        case TRUE:
        case INTEGER_LITERAL:
        case FLOATING_POINT_LITERAL:
        case CHARACTER_LITERAL:
        case STRING_LITERAL:
          Literal();
          break;
        default:
          jj_la1[110] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
        break;
      default:
        jj_la1[111] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

// To fix bug Test48.java. Clemens [2000-10-03]
  final public void PostfixLookahead() throws ParseException {
    jj_consume_token(LPAREN);
    Name();
    label_49:
    while (true) {
      if (jj_2_26(2)) {
        ;
      } else {
        break label_49;
      }
      jj_consume_token(LBRACKET);
      jj_consume_token(RBRACKET);
    }
    jj_consume_token(DOT);
  }

  final public void PostfixExpression() throws ParseException {
    PrimaryExpression();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case INCR:
    case DECR:
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case INCR:
        jj_consume_token(INCR);
        break;
      case DECR:
        jj_consume_token(DECR);
        break;
      default:
        jj_la1[112] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      break;
    default:
      jj_la1[113] = jj_gen;
      ;
    }
  }

  final public void CastExpression() throws ParseException {
    if (jj_2_27(2147483647)) {
      jj_consume_token(LPAREN);
      Type();
      jj_consume_token(RPAREN);
      UnaryExpression();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LPAREN:
        jj_consume_token(LPAREN);
        Type();
        jj_consume_token(RPAREN);
        UnaryExpressionNotPlusMinus();
        break;
      default:
        jj_la1[114] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

  final public void PrimaryExpression() throws ParseException {
    PrimaryPrefix();
    label_50:
    while (true) {
      if (jj_2_28(2)) {
        ;
      } else {
        break label_50;
      }
      PrimarySuffix();
    }
  }

  final public void PrimaryPrefix() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case FALSE:
    case NULL:
    case TRUE:
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case CHARACTER_LITERAL:
    case STRING_LITERAL:
      Literal();
      break;
    case THIS:
      jj_consume_token(THIS);
      break;
    default:
      jj_la1[115] = jj_gen;
      if (jj_2_30(2)) {
        jj_consume_token(SUPER);
        jj_consume_token(DOT);
        Identifier();
      } else {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case LPAREN:
          jj_consume_token(LPAREN);
          Expression();
          jj_consume_token(RPAREN);
          break;
        case NEW:
          AllocationExpression();
          break;
        default:
          jj_la1[116] = jj_gen;
          if (jj_2_31(2147483647)) {
            ResultType();
            jj_consume_token(DOT);
            jj_consume_token(CLASS);
          } else {
            switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
            case ASSERT:
            case ENUM:
            case IDENTIFIER:
              Name();
              if (jj_2_29(3)) {
                jj_consume_token(DOT);
                jj_consume_token(SUPER);
                jj_consume_token(DOT);
                Identifier();
              } else {
                ;
              }
              break;
            default:
              jj_la1[117] = jj_gen;
              jj_consume_token(-1);
              throw new ParseException();
            }
          }
        }
      }
    }
  }

  final public void PrimarySuffix() throws ParseException {
    if (jj_2_32(2)) {
      jj_consume_token(DOT);
      jj_consume_token(THIS);
    } else if (jj_2_33(2)) {
      jj_consume_token(DOT);
      AllocationExpression();
    } else if (jj_2_34(3)) {
      MemberSelector();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LBRACKET:
        jj_consume_token(LBRACKET);
        Expression();
        jj_consume_token(RBRACKET);
        break;
      case DOT:
        jj_consume_token(DOT);
        Identifier();
        break;
      case LPAREN:
        Arguments();
        break;
      default:
        jj_la1[118] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

  final public void Literal() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case INTEGER_LITERAL:
      jj_consume_token(INTEGER_LITERAL);
      break;
    case FLOATING_POINT_LITERAL:
      jj_consume_token(FLOATING_POINT_LITERAL);
      break;
    case CHARACTER_LITERAL:
      jj_consume_token(CHARACTER_LITERAL);
      break;
    case STRING_LITERAL:
      jj_consume_token(STRING_LITERAL);
      break;
    case FALSE:
    case TRUE:
      BooleanLiteral();
      break;
    case NULL:
      NullLiteral();
      break;
    default:
      jj_la1[119] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void BooleanLiteral() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case TRUE:
      jj_consume_token(TRUE);
      break;
    case FALSE:
      jj_consume_token(FALSE);
      break;
    default:
      jj_la1[120] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void NullLiteral() throws ParseException {
    jj_consume_token(NULL);
  }

  final public void Arguments() throws ParseException {
    jj_consume_token(LPAREN);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ASSERT:
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case ENUM:
    case FALSE:
    case FLOAT:
    case INT:
    case LONG:
    case NEW:
    case NULL:
    case SHORT:
    case SUPER:
    case THIS:
    case TRUE:
    case VOID:
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case CHARACTER_LITERAL:
    case STRING_LITERAL:
    case IDENTIFIER:
    case LPAREN:
    case BANG:
    case TILDE:
    case INCR:
    case DECR:
    case PLUS:
    case MINUS:
      ArgumentList();
      break;
    default:
      jj_la1[121] = jj_gen;
      ;
    }
    jj_consume_token(RPAREN);
  }

  final public void ArgumentList() throws ParseException {
    Expression();
    label_51:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case COMMA:
        ;
        break;
      default:
        jj_la1[122] = jj_gen;
        break label_51;
      }
      jj_consume_token(COMMA);
      Expression();
    }
  }

  final public void AllocationExpression() throws ParseException {
        String sOldClass = _sClass;
        int oldNcss = _ncss;
        int oldFunctions = _functions;
        int oldClasses = _classes;
        String sName;
    if (jj_2_35(2)) {
      jj_consume_token(NEW);
      PrimitiveType();
      ArrayDimsAndInits();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case NEW:
        jj_consume_token(NEW);
        Name();
                          sName = _sName;
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case LT:
          TypeArguments();
          break;
        default:
          jj_la1[123] = jj_gen;
          ;
        }
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case LBRACKET:
          ArrayDimsAndInits();
          break;
        case LPAREN:
          Arguments();
          switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
          case LBRACE:
                         if (!_sClass.equals("")) {
                                 _sClass += ".";
                         }
                         /*_sClass += sName;*/
                         _sClass += sName + "$" + _anonClassCount++;
                         _classLevel ++;
            ClassBody();
                                _classLevel--;
                                _functions = oldFunctions;
                                _classes = oldClasses + 1;
                                _sClass = sOldClass;
            break;
          default:
            jj_la1[124] = jj_gen;
            ;
          }
          break;
        default:
          jj_la1[125] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
        break;
      default:
        jj_la1[126] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

/*
 * The third LOOKAHEAD specification below is to parse to PrimarySuffix
 * if there is an expression between the "[...]".
 */
  final public void ArrayDimsAndInits() throws ParseException {
    if (jj_2_38(2)) {
      label_52:
      while (true) {
        jj_consume_token(LBRACKET);
        Expression();
        jj_consume_token(RBRACKET);
        if (jj_2_36(2)) {
          ;
        } else {
          break label_52;
        }
      }
      label_53:
      while (true) {
        if (jj_2_37(2)) {
          ;
        } else {
          break label_53;
        }
        jj_consume_token(LBRACKET);
        jj_consume_token(RBRACKET);
      }
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LBRACKET:
        label_54:
        while (true) {
          jj_consume_token(LBRACKET);
          jj_consume_token(RBRACKET);
          switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
          case LBRACKET:
            ;
            break;
          default:
            jj_la1[127] = jj_gen;
            break label_54;
          }
        }
        ArrayInitializer();
        break;
      default:
        jj_la1[128] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

/*
 * Statement syntax follows.
 */
  final public void Statement() throws ParseException {
        _bReturn = false;
    if (jj_2_39(2)) {
      LabeledStatement();
    } else if (jj_2_40(2147483647)) {
      AssertStatement();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LBRACE:
        Block();
        break;
      case SEMICOLON:
        EmptyStatement();
        break;
      case ASSERT:
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case DOUBLE:
      case ENUM:
      case FALSE:
      case FLOAT:
      case INT:
      case LONG:
      case NEW:
      case NULL:
      case SHORT:
      case SUPER:
      case THIS:
      case TRUE:
      case VOID:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case CHARACTER_LITERAL:
      case STRING_LITERAL:
      case IDENTIFIER:
      case LPAREN:
      case INCR:
      case DECR:
        StatementExpression();
        jj_consume_token(SEMICOLON);
          _ncss++;       
        break;
      case SWITCH:
        SwitchStatement();
        break;
      case IF:
        IfStatement();
          _cyc++;
        break;
      case WHILE:
        WhileStatement();
          _cyc++;
        break;
      case DO:
        DoStatement();
          _cyc++;
        break;
      case FOR:
        ForStatement();
          _cyc++;
        break;
      case BREAK:
        BreakStatement();
        break;
      case CONTINUE:
        ContinueStatement();
        break;
      case RETURN:
        ReturnStatement();
        break;
      case THROW:
        ThrowStatement();
        break;
      case SYNCHRONIZED:
        SynchronizedStatement();
        break;
      case TRY:
        TryStatement();
        break;
      default:
        jj_la1[129] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

  final public void LabeledStatement() throws ParseException {
    Identifier();
    jj_consume_token(COLON);
    Statement();
          _ncss++;       
  }

  final public void AssertStatementLookahead() throws ParseException {
    jj_consume_token(ASSERT);
    Expression();
  }

  final public void AssertStatement() throws ParseException {
    jj_consume_token(ASSERT);
    Expression();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case COLON:
      jj_consume_token(COLON);
      Expression();
      break;
    default:
      jj_la1[130] = jj_gen;
      ;
    }
    jj_consume_token(SEMICOLON);
    _ncss++;       
  }

  final public void Block() throws ParseException {
    jj_consume_token(LBRACE);
    label_55:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case ASSERT:
      case BOOLEAN:
      case BREAK:
      case BYTE:
      case CHAR:
      case CLASS:
      case CONTINUE:
      case DO:
      case DOUBLE:
      case ENUM:
      case FALSE:
      case FINAL:
      case FLOAT:
      case FOR:
      case IF:
      case INT:
      case INTERFACE:
      case LONG:
      case NATIVE:
      case NEW:
      case NULL:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case RETURN:
      case SHORT:
      case STATIC:
      case TESTAAAA:
      case SUPER:
      case SWITCH:
      case SYNCHRONIZED:
      case THIS:
      case THROW:
      case TRANSIENT:
      case TRUE:
      case TRY:
      case VOID:
      case VOLATILE:
      case WHILE:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case CHARACTER_LITERAL:
      case STRING_LITERAL:
      case IDENTIFIER:
      case LPAREN:
      case LBRACE:
      case SEMICOLON:
      case AT:
      case INCR:
      case DECR:
        ;
        break;
      default:
        jj_la1[131] = jj_gen;
        break label_55;
      }
      BlockStatement();
    }
    jj_consume_token(RBRACE);
  }

  final public void BlockStatement() throws ParseException {
    if (jj_2_41(2147483647)) {
      LocalVariableDeclaration();
      jj_consume_token(SEMICOLON);
                _ncss++;       
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ASSERT:
      case BOOLEAN:
      case BREAK:
      case BYTE:
      case CHAR:
      case CONTINUE:
      case DO:
      case DOUBLE:
      case ENUM:
      case FALSE:
      case FLOAT:
      case FOR:
      case IF:
      case INT:
      case LONG:
      case NEW:
      case NULL:
      case RETURN:
      case SHORT:
      case SUPER:
      case SWITCH:
      case SYNCHRONIZED:
      case THIS:
      case THROW:
      case TRUE:
      case TRY:
      case VOID:
      case WHILE:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case CHARACTER_LITERAL:
      case STRING_LITERAL:
      case IDENTIFIER:
      case LPAREN:
      case LBRACE:
      case SEMICOLON:
      case INCR:
      case DECR:
        Statement();
        break;
      case ABSTRACT:
      case CLASS:
      case FINAL:
      case NATIVE:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case STATIC:
      case TESTAAAA:
      case TRANSIENT:
      case VOLATILE:
      case AT:
        UnmodifiedClassDeclaration();
        break;
      case INTERFACE:
        UnmodifiedInterfaceDeclaration();
        break;
      default:
        jj_la1[132] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

  final public void LocalVariableDeclaration() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case FINAL:
      jj_consume_token(FINAL);
      break;
    case AT:
        Annotation();
        break;
    default:
      jj_la1[133] = jj_gen;
      ;
    }
    Type();
    VariableDeclarator();
    label_56:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case COMMA:
        ;
        break;
      default:
        jj_la1[134] = jj_gen;
        break label_56;
      }
      jj_consume_token(COMMA);
      VariableDeclarator();
    }
  }

  final public void EmptyStatement() throws ParseException {
    jj_consume_token(SEMICOLON);
  }

  final public void StatementExpression() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case INCR:
      PreIncrementExpression();
      break;
    case DECR:
      PreDecrementExpression();
      break;
    case ASSERT:
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case ENUM:
    case FALSE:
    case FLOAT:
    case INT:
    case LONG:
    case NEW:
    case NULL:
    case SHORT:
    case SUPER:
    case THIS:
    case TRUE:
    case VOID:
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case CHARACTER_LITERAL:
    case STRING_LITERAL:
    case IDENTIFIER:
    case LPAREN:
      PrimaryExpression();
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ASSIGN:
      case INCR:
      case DECR:
      case PLUSASSIGN:
      case MINUSASSIGN:
      case STARASSIGN:
      case SLASHASSIGN:
      case ANDASSIGN:
      case ORASSIGN:
      case XORASSIGN:
      case REMASSIGN:
      case LSHIFTASSIGN:
      case RSIGNEDSHIFTASSIGN:
      case RUNSIGNEDSHIFTASSIGN:
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case INCR:
          jj_consume_token(INCR);
          break;
        case DECR:
          jj_consume_token(DECR);
          break;
        case ASSIGN:
        case PLUSASSIGN:
        case MINUSASSIGN:
        case STARASSIGN:
        case SLASHASSIGN:
        case ANDASSIGN:
        case ORASSIGN:
        case XORASSIGN:
        case REMASSIGN:
        case LSHIFTASSIGN:
        case RSIGNEDSHIFTASSIGN:
        case RUNSIGNEDSHIFTASSIGN:
          AssignmentOperator();
          Expression();
          break;
        default:
          jj_la1[135] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
        break;
      default:
        jj_la1[136] = jj_gen;
        ;
      }
      break;
    default:
      jj_la1[137] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void SwitchStatement() throws ParseException {
                _localCases = 0;
    jj_consume_token(SWITCH);
    jj_consume_token(LPAREN);
    Expression();
    jj_consume_token(RPAREN);
    jj_consume_token(LBRACE);
    label_57:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case CASE:
      case _DEFAULT:
        ;
        break;
      default:
        jj_la1[138] = jj_gen;
        break label_57;
      }
      SwitchLabel();
      label_58:
      while (true) {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case ABSTRACT:
        case ASSERT:
        case BOOLEAN:
        case BREAK:
        case BYTE:
        case CHAR:
        case CLASS:
        case CONTINUE:
        case DO:
        case DOUBLE:
        case ENUM:
        case FALSE:
        case FINAL:
        case FLOAT:
        case FOR:
        case IF:
        case INT:
        case INTERFACE:
        case LONG:
        case NATIVE:
        case NEW:
        case NULL:
        case PRIVATE:
        case PROTECTED:
        case PUBLIC:
        case RETURN:
        case SHORT:
        case STATIC:
        case TESTAAAA:
        case SUPER:
        case SWITCH:
        case SYNCHRONIZED:
        case THIS:
        case THROW:
        case TRANSIENT:
        case TRUE:
        case TRY:
        case VOID:
        case VOLATILE:
        case WHILE:
        case INTEGER_LITERAL:
        case FLOATING_POINT_LITERAL:
        case CHARACTER_LITERAL:
        case STRING_LITERAL:
        case IDENTIFIER:
        case LPAREN:
        case LBRACE:
        case SEMICOLON:
        case AT:
        case INCR:
        case DECR:
          ;
          break;
        default:
          jj_la1[139] = jj_gen;
          break label_58;
        }
        BlockStatement();
      }
    }
    jj_consume_token(RBRACE);
          _ncss++;       
  }

  final public void SwitchLabel() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case CASE:
      jj_consume_token(CASE);
      Expression();
      jj_consume_token(COLON);
                _ncss++;
                _localCases++;
                _cyc++;
      break;
    case _DEFAULT:
      jj_consume_token(_DEFAULT);
      jj_consume_token(COLON);
                _ncss++;       
      break;
    default:
      jj_la1[140] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void IfStatement() throws ParseException {
    jj_consume_token(IF);
    jj_consume_token(LPAREN);
    Expression();
    jj_consume_token(RPAREN);
    Statement();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ELSE:
      jj_consume_token(ELSE);
                                                                _ncss++;       
      Statement();
      break;
    default:
      jj_la1[141] = jj_gen;
      ;
    }
          _ncss++;       
  }

  final public void WhileStatement() throws ParseException {
    jj_consume_token(WHILE);
    jj_consume_token(LPAREN);
    Expression();
    jj_consume_token(RPAREN);
    Statement();
          _ncss++;       
  }

  final public void DoStatement() throws ParseException {
    jj_consume_token(DO);
    Statement();
    jj_consume_token(WHILE);
    jj_consume_token(LPAREN);
    Expression();
    jj_consume_token(RPAREN);
    jj_consume_token(SEMICOLON);
          _ncss++;       
  }

  final public void ForStatement() throws ParseException {
    jj_consume_token(FOR);
    jj_consume_token(LPAREN);
    if (jj_2_42(2147483647)) {
      Modifiers();
      Type();
      jj_consume_token(IDENTIFIER);
      jj_consume_token(COLON);
      Expression();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ASSERT:
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case DOUBLE:
      case ENUM:
      case FALSE:
      case FINAL:
      case FLOAT:
      case INT:
      case LONG:
      case NEW:
      case NULL:
      case SHORT:
      case SUPER:
      case THIS:
      case TRUE:
      case VOID:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case CHARACTER_LITERAL:
      case STRING_LITERAL:
      case IDENTIFIER:
      case LPAREN:
      case SEMICOLON:
      case INCR:
      case DECR:
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case ASSERT:
        case BOOLEAN:
        case BYTE:
        case CHAR:
        case DOUBLE:
        case ENUM:
        case FALSE:
        case FINAL:
        case FLOAT:
        case INT:
        case LONG:
        case NEW:
        case NULL:
        case SHORT:
        case SUPER:
        case THIS:
        case TRUE:
        case VOID:
        case INTEGER_LITERAL:
        case FLOATING_POINT_LITERAL:
        case CHARACTER_LITERAL:
        case STRING_LITERAL:
        case IDENTIFIER:
        case LPAREN:
        case INCR:
        case DECR:
          ForInit();
          break;
        default:
          jj_la1[142] = jj_gen;
          ;
        }
        jj_consume_token(SEMICOLON);
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case ASSERT:
        case BOOLEAN:
        case BYTE:
        case CHAR:
        case DOUBLE:
        case ENUM:
        case FALSE:
        case FLOAT:
        case INT:
        case LONG:
        case NEW:
        case NULL:
        case SHORT:
        case SUPER:
        case THIS:
        case TRUE:
        case VOID:
        case INTEGER_LITERAL:
        case FLOATING_POINT_LITERAL:
        case CHARACTER_LITERAL:
        case STRING_LITERAL:
        case IDENTIFIER:
        case LPAREN:
        case BANG:
        case TILDE:
        case INCR:
        case DECR:
        case PLUS:
        case MINUS:
          Expression();
          break;
        default:
          jj_la1[143] = jj_gen;
          ;
        }
        jj_consume_token(SEMICOLON);
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case ASSERT:
        case BOOLEAN:
        case BYTE:
        case CHAR:
        case DOUBLE:
        case ENUM:
        case FALSE:
        case FLOAT:
        case INT:
        case LONG:
        case NEW:
        case NULL:
        case SHORT:
        case SUPER:
        case THIS:
        case TRUE:
        case VOID:
        case INTEGER_LITERAL:
        case FLOATING_POINT_LITERAL:
        case CHARACTER_LITERAL:
        case STRING_LITERAL:
        case IDENTIFIER:
        case LPAREN:
        case INCR:
        case DECR:
          ForUpdate();
          break;
        default:
          jj_la1[144] = jj_gen;
          ;
        }
        break;
      default:
        jj_la1[145] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
    jj_consume_token(RPAREN);
    Statement();
          _ncss++;       
  }

  final public void ForInit() throws ParseException {
    if (jj_2_43(2147483647)) {
      LocalVariableDeclaration();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ASSERT:
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case DOUBLE:
      case ENUM:
      case FALSE:
      case FLOAT:
      case INT:
      case LONG:
      case NEW:
      case NULL:
      case SHORT:
      case SUPER:
      case THIS:
      case TRUE:
      case VOID:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case CHARACTER_LITERAL:
      case STRING_LITERAL:
      case IDENTIFIER:
      case LPAREN:
      case INCR:
      case DECR:
        StatementExpressionList();
        break;
      default:
        jj_la1[146] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

  final public void StatementExpressionList() throws ParseException {
    StatementExpression();
    label_59:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case COMMA:
        ;
        break;
      default:
        jj_la1[147] = jj_gen;
        break label_59;
      }
      jj_consume_token(COMMA);
      StatementExpression();
    }
  }

  final public void ForUpdate() throws ParseException {
    StatementExpressionList();
  }

  final public void BreakStatement() throws ParseException {
    jj_consume_token(BREAK);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ASSERT:
    case IDENTIFIER:
      Identifier();
      break;
    default:
      jj_la1[148] = jj_gen;
      ;
    }
    jj_consume_token(SEMICOLON);
          _ncss++;       
  }

  final public void ContinueStatement() throws ParseException {
    jj_consume_token(CONTINUE);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ASSERT:
    case IDENTIFIER:
      Identifier();
      break;
    default:
      jj_la1[149] = jj_gen;
      ;
    }
    jj_consume_token(SEMICOLON);
          _ncss++;       
  }

  final public void ReturnStatement() throws ParseException {
    jj_consume_token(RETURN);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ASSERT:
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case ENUM:
    case FALSE:
    case FLOAT:
    case INT:
    case LONG:
    case NEW:
    case NULL:
    case SHORT:
    case SUPER:
    case THIS:
    case TRUE:
    case VOID:
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case CHARACTER_LITERAL:
    case STRING_LITERAL:
    case IDENTIFIER:
    case LPAREN:
    case BANG:
    case TILDE:
    case INCR:
    case DECR:
    case PLUS:
    case MINUS:
      Expression();
      break;
    default:
      jj_la1[150] = jj_gen;
      ;
    }
    jj_consume_token(SEMICOLON);
                _ncss++;
                _cyc++;
                _bReturn = true;
  }

  final public void ThrowStatement() throws ParseException {
    jj_consume_token(THROW);
    Expression();
    jj_consume_token(SEMICOLON);
                _ncss++;
                _cyc++;
  }

  final public void SynchronizedStatement() throws ParseException {
    jj_consume_token(SYNCHRONIZED);
    jj_consume_token(LPAREN);
    Expression();
    jj_consume_token(RPAREN);
    Block();
          _ncss++;       
  }

  final public void TryStatement() throws ParseException {
    jj_consume_token(TRY);
    Block();
    label_60:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case CATCH:
        ;
        break;
      default:
        jj_la1[151] = jj_gen;
        break label_60;
      }
      jj_consume_token(CATCH);
      jj_consume_token(LPAREN);
      FormalParameter();
      jj_consume_token(RPAREN);
      Block();
                                                _ncss++;        _cyc++;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case FINALLY:
      jj_consume_token(FINALLY);
      Block();
                        _ncss++;       
      break;
    default:
      jj_la1[152] = jj_gen;
      ;
    }
  }

  final public void Identifier() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case IDENTIFIER:
      jj_consume_token(IDENTIFIER);
      break;
    case ASSERT:
      jj_consume_token(ASSERT);
      break;
    default:
      jj_la1[153] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

/* Annotation syntax follows. */
  final public void Annotation() throws ParseException {
    if (jj_2_44(2147483647)) {
      NormalAnnotation();
    } else if (jj_2_45(2147483647)) {
      SingleMemberAnnotation();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AT:
        MarkerAnnotation();
        break;
      default:
        jj_la1[154] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

  final public void NormalAnnotation() throws ParseException {
    jj_consume_token(AT);
    Name();
    jj_consume_token(LPAREN);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case IDENTIFIER:
      MemberValuePairs();
      break;
    default:
      jj_la1[155] = jj_gen;
      ;
    }
    jj_consume_token(RPAREN);
  }

  final public void MarkerAnnotation() throws ParseException {
    jj_consume_token(AT);
    Name();
  }

  final public void SingleMemberAnnotation() throws ParseException {
    jj_consume_token(AT);
    Name();
    jj_consume_token(LPAREN);
    MemberValue();
    jj_consume_token(RPAREN);
  }

  final public void MemberValuePairs() throws ParseException {
    MemberValuePair();
    label_61:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case COMMA:
        ;
        break;
      default:
        jj_la1[156] = jj_gen;
        break label_61;
      }
      jj_consume_token(COMMA);
      MemberValuePair();
    }
  }

  final public void MemberValuePair() throws ParseException {
    jj_consume_token(IDENTIFIER);
    jj_consume_token(ASSIGN);
    MemberValue();
  }

  final public void MemberValue() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case AT:
      Annotation();
      break;
    case LBRACE:
      MemberValueArrayInitializer();
      break;
    case ASSERT:
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case ENUM:
    case FALSE:
    case FLOAT:
    case INT:
    case LONG:
    case NEW:
    case NULL:
    case SHORT:
    case SUPER:
    case THIS:
    case TRUE:
    case VOID:
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case CHARACTER_LITERAL:
    case STRING_LITERAL:
    case IDENTIFIER:
    case LPAREN:
    case BANG:
    case TILDE:
    case INCR:
    case DECR:
    case PLUS:
    case MINUS:
      ConditionalExpression();
      break;
    default:
      jj_la1[157] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void MemberValueArrayInitializer() throws ParseException {
    jj_consume_token(LBRACE);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ASSERT:
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case ENUM:
    case FALSE:
    case FLOAT:
    case INT:
    case LONG:
    case NEW:
    case NULL:
    case SHORT:
    case SUPER:
    case THIS:
    case TRUE:
    case VOID:
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case CHARACTER_LITERAL:
    case STRING_LITERAL:
    case IDENTIFIER:
    case LPAREN:
    case LBRACE:
    case AT:
    case BANG:
    case TILDE:
    case INCR:
    case DECR:
    case PLUS:
    case MINUS:
      MemberValue();
      break;
    default:
      jj_la1[158] = jj_gen;
      ;
    }
    label_62:
    while (true) {
      if (jj_2_46(2)) {
        ;
      } else {
        break label_62;
      }
      jj_consume_token(COMMA);
      MemberValue();
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case COMMA:
      jj_consume_token(COMMA);
      break;
    default:
      jj_la1[159] = jj_gen;
      ;
    }
    jj_consume_token(RBRACE);
  }

/*
 =================================================
 Java 1.5 stuff starts here
 =================================================
*/

/* Annotation Types. */
  final public void AnnotationTypeDeclaration(int modifiers) throws ParseException {
    jj_consume_token(AT);
    jj_consume_token(INTERFACE);
    jj_consume_token(IDENTIFIER);
    AnnotationTypeBody();
  }

  final public void AnnotationTypeBody() throws ParseException {
    jj_consume_token(LBRACE);
       _ncss++;       
    label_63:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case ASSERT:
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case CLASS:
      case DOUBLE:
      case ENUM:
      case FINAL:
      case FLOAT:
      case INT:
      case INTERFACE:
      case LONG:
      case NATIVE:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case SHORT:
      case STATIC:
      case TESTAAAA:
      case SYNCHRONIZED:
      case TRANSIENT:
      case VOLATILE:
      case IDENTIFIER:
      case SEMICOLON:
      case AT:
        ;
        break;
      default:
        jj_la1[160] = jj_gen;
        break label_63;
      }
      AnnotationTypeMemberDeclaration();
    }
    jj_consume_token(RBRACE);
  }

  final public void AnnotationTypeMemberDeclaration() throws ParseException {
   int modifiers;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ABSTRACT:
    case ASSERT:
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case CLASS:
    case DOUBLE:
    case ENUM:
    case FINAL:
    case FLOAT:
    case INT:
    case INTERFACE:
    case LONG:
    case NATIVE:
    case PRIVATE:
    case PROTECTED:
    case PUBLIC:
    case SHORT:
    case STATIC:
    case TESTAAAA:
    case SYNCHRONIZED:
    case TRANSIENT:
    case VOLATILE:
    case IDENTIFIER:
    case AT:
      modifiers = Modifiers();
      if (jj_2_47(2147483647)) {
        Type();
        jj_consume_token(IDENTIFIER);
        jj_consume_token(LPAREN);
        jj_consume_token(RPAREN);
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case _DEFAULT:
          DefaultValue();
          break;
        default:
          jj_la1[161] = jj_gen;
          ;
        }
        jj_consume_token(SEMICOLON);
            _ncss++;
            
      } else {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case CLASS:
        case INTERFACE:
          ClassOrInterfaceDeclaration(modifiers);
          break;
        case ENUM:
          EnumDeclaration(modifiers);
          break;
        case AT:
          AnnotationTypeDeclaration(modifiers);
          break;
        case ASSERT:
        case BOOLEAN:
        case BYTE:
        case CHAR:
        case DOUBLE:
        case FLOAT:
        case INT:
        case LONG:
        case SHORT:
        case IDENTIFIER:
          FieldDeclaration15(modifiers);
          break;
        default:
          jj_la1[162] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
      }
      break;
    case SEMICOLON:
      jj_consume_token(SEMICOLON);
            _ncss++;
            
      break;
    default:
      jj_la1[163] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void DefaultValue() throws ParseException {
    jj_consume_token(_DEFAULT);
    MemberValue();
  }

/*
 * Modifiers. We match all modifiers in a single rule to reduce the chances of
 * syntax errors for simple modifier mistakes. It will also enable us to give
 * better error messages.
 */
  final public int Modifiers() throws ParseException {
   int modifiers = 0;
   _tmpToken = null;
    label_64:
    while (true) {
      if (jj_2_48(2)) {
        ;
      } else {
        break label_64;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PUBLIC:
        jj_consume_token(PUBLIC);
              modifiers |= ModifierSet.PUBLIC;
      if ( _tmpToken == null ) {
          _tmpToken = getToken( 0 );
      }
        break;
      case STATIC:
        jj_consume_token(STATIC);
              modifiers |= ModifierSet.STATIC;       if ( _tmpToken == null ) {
          _tmpToken = getToken( 0 );
      }
        break;
      case PROTECTED:
        jj_consume_token(PROTECTED);
                 modifiers |= ModifierSet.PROTECTED;       if ( _tmpToken == null ) {
       _tmpToken = getToken( 0 );
      }
        break;
      case PRIVATE:
        jj_consume_token(PRIVATE);
               modifiers |= ModifierSet.PRIVATE;       if ( _tmpToken == null ) {
          _tmpToken = getToken( 0 );
      }
        break;
      case FINAL:
        jj_consume_token(FINAL);
             modifiers |= ModifierSet.FINAL;       if ( _tmpToken == null ) {
       _tmpToken = getToken( 0 );
      }
        break;
      case ABSTRACT:
        jj_consume_token(ABSTRACT);
                modifiers |= ModifierSet.ABSTRACT;       if ( _tmpToken == null ) {
          _tmpToken = getToken( 0 );
      }
        break;
      case SYNCHRONIZED:
        jj_consume_token(SYNCHRONIZED);
                    modifiers |= ModifierSet.SYNCHRONIZED;       if ( _tmpToken == null ) {
          _tmpToken = getToken( 0 );
      }
        break;
      case NATIVE:
        jj_consume_token(NATIVE);
              modifiers |= ModifierSet.NATIVE;       if ( _tmpToken == null ) {
          _tmpToken = getToken( 0 );
      }
        break;
      case TRANSIENT:
        jj_consume_token(TRANSIENT);
                 modifiers |= ModifierSet.TRANSIENT;       if ( _tmpToken == null ) {
          _tmpToken = getToken( 0 );
      }
        break;
      case VOLATILE:
        jj_consume_token(VOLATILE);
                modifiers |= ModifierSet.VOLATILE;       if ( _tmpToken == null ) {
          _tmpToken = getToken( 0 );
      }
        break;
      case TESTAAAA:
        jj_consume_token(TESTAAAA);
                modifiers |= ModifierSet.STRICTFP;       if ( _tmpToken == null ) {
          _tmpToken = getToken( 0 );
      }
        break;
      case AT:
        Annotation();
        break;
      default:
        jj_la1[164] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
    {if (true) return modifiers;}
    throw new Error("Missing return statement in function");
  }

  final public void ClassOrInterfaceDeclaration(int modifiers) throws ParseException {
   boolean isInterface = false;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case CLASS:
      jj_consume_token(CLASS);
      break;
    case INTERFACE:
      jj_consume_token(INTERFACE);
                            isInterface = true;
      break;
    default:
      jj_la1[165] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    jj_consume_token(IDENTIFIER);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LT:
      TypeParameters();
      break;
    default:
      jj_la1[166] = jj_gen;
      ;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case EXTENDS:
      ExtendsList(isInterface);
      break;
    default:
      jj_la1[167] = jj_gen;
      ;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case IMPLEMENTS:
      ImplementsList(isInterface);
      break;
    default:
      jj_la1[168] = jj_gen;
      ;
    }
    ClassOrInterfaceBody(isInterface);
  }

  final public void EnumDeclaration(int modifiers) throws ParseException {
        String sOldClass = _sClass;
        int oldClasses = _classes;
        int oldNcss = _ncss;
        int oldFunctions = _functions;

        // Chris Povirk
        int oldSingle;
        int oldMulti;
      
    jj_consume_token(ENUM);
    jj_consume_token(IDENTIFIER);
                if (!_sClass.equals("")) {
                        _sClass += ".";
                }
                _sClass += new String(getToken(0).image);
                _classLevel ++;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case IMPLEMENTS:
      ImplementsList(false);
      break;
    default:
      jj_la1[169] = jj_gen;
      ;
    }
        // Chris Povirk
      oldSingle = token_source._iSingleComments;
      oldMulti = token_source._iMultiComments;
    EnumBody();
             _classLevel--;
             if (_classLevel == 0) {
                 //_topLevelClasses++;
                 Vector vMetrics = new Vector();
                 vMetrics.addElement(new String(_sPackage + _sClass));
                 vMetrics.addElement(new Integer(_ncss - oldNcss));
                 vMetrics.addElement(new Integer(_functions - oldFunctions));
                 vMetrics.addElement(new Integer(_classes - oldClasses));
                 Token lastToken = getToken( 0 );
                 vMetrics.addElement( new Integer( lastToken.endLine ) );
                 vMetrics.addElement( new Integer( lastToken.endColumn ) );
                 vMetrics.addElement( new Integer( _javadocs ) );

                 // Chris Povirk
                 vMetrics.addElement( new Integer(_jvdcLines));
                 vMetrics.addElement( new Integer(token_source._iSingleComments - oldSingle));
                 vMetrics.addElement( new Integer(token_source._iMultiComments - oldMulti));

                 _vClasses.addElement(vMetrics);
                 _pPackageMetric.functions += _functions - oldFunctions;
                 _pPackageMetric.classes++;

                 // added by SMS
                 _pPackageMetric.javadocs += _javadocs;
                 //_pPackageMetric.javadocsLn += token_source._iFormalComments - oldFormal;
                 //_pPackageMetric.singleLn += token_source._iSingleComments - oldSingle;
                 //_pPackageMetric.multiLn += token_source._iMultiComments - oldMulti;
                 //
             }
             _functions = oldFunctions;
             _classes = oldClasses + 1;
             _sClass = sOldClass;
  }

  final public void TypeParameters() throws ParseException {
    jj_consume_token(LT);
    TypeParameter();
    label_65:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case COMMA:
        ;
        break;
      default:
        jj_la1[170] = jj_gen;
        break label_65;
      }
      jj_consume_token(COMMA);
      TypeParameter();
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case GT:
    case RSIGNEDSHIFT:
    case RUNSIGNEDSHIFT:
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case RUNSIGNEDSHIFT:
        jj_consume_token(RUNSIGNEDSHIFT);
        break;
      case RSIGNEDSHIFT:
        jj_consume_token(RSIGNEDSHIFT);
        break;
      case GT:
        jj_consume_token(GT);
        break;
      default:
        jj_la1[171] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      break;
    default:
      jj_la1[172] = jj_gen;
      ;
    }
  }

  final public void ExtendsList(boolean isInterface) throws ParseException {
   boolean extendsMoreThanOne = false;
    jj_consume_token(EXTENDS);
    ClassOrInterfaceType();
    label_66:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case COMMA:
        ;
        break;
      default:
        jj_la1[173] = jj_gen;
        break label_66;
      }
      jj_consume_token(COMMA);
      ClassOrInterfaceType();
                                  extendsMoreThanOne = true;
    }
      if (extendsMoreThanOne && !isInterface)
         {if (true) throw new ParseException("A class cannot extend more than one other class");}
  }

  final public void ImplementsList(boolean isInterface) throws ParseException {
    jj_consume_token(IMPLEMENTS);
    ClassOrInterfaceType();
    label_67:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case COMMA:
        ;
        break;
      default:
        jj_la1[174] = jj_gen;
        break label_67;
      }
      jj_consume_token(COMMA);
      ClassOrInterfaceType();
    }
      if (isInterface)
         {if (true) throw new ParseException("An interface cannot implement other interfaces");}
  }

  final public void ClassOrInterfaceBody(boolean isInterface) throws ParseException {
    jj_consume_token(LBRACE);
           _ncss++; 
    label_68:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case ASSERT:
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case CLASS:
      case DOUBLE:
      case ENUM:
      case FINAL:
      case FLOAT:
      case INT:
      case INTERFACE:
      case LONG:
      case NATIVE:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case SHORT:
      case STATIC:
      case TESTAAAA:
      case SYNCHRONIZED:
      case TRANSIENT:
      case VOID:
      case VOLATILE:
      case IDENTIFIER:
      case LBRACE:
      case SEMICOLON:
      case AT:
      case LT:
        ;
        break;
      default:
        jj_la1[175] = jj_gen;
        break label_68;
      }
      ClassOrInterfaceBodyDeclaration(isInterface);
    }
    jj_consume_token(RBRACE);
  }

  final public void EnumBody() throws ParseException {
    jj_consume_token(LBRACE);
               _ncss++;
               
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ABSTRACT:
    case FINAL:
    case NATIVE:
    case PRIVATE:
    case PROTECTED:
    case PUBLIC:
    case STATIC:
    case TESTAAAA:
    case SYNCHRONIZED:
    case TRANSIENT:
    case VOLATILE:
    case IDENTIFIER:
    case AT:
      EnumConstant();
      label_69:
      while (true) {
        if (jj_2_49(2)) {
          ;
        } else {
          break label_69;
        }
        jj_consume_token(COMMA);
        EnumConstant();
      }
      break;
    default:
      jj_la1[176] = jj_gen;
      ;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case COMMA:
      jj_consume_token(COMMA);
      break;
    default:
      jj_la1[177] = jj_gen;
      ;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case SEMICOLON:
      jj_consume_token(SEMICOLON);
      label_70:
      while (true) {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case ABSTRACT:
        case ASSERT:
        case BOOLEAN:
        case BYTE:
        case CHAR:
        case CLASS:
        case DOUBLE:
        case ENUM:
        case FINAL:
        case FLOAT:
        case INT:
        case INTERFACE:
        case LONG:
        case NATIVE:
        case PRIVATE:
        case PROTECTED:
        case PUBLIC:
        case SHORT:
        case STATIC:
        case TESTAAAA:
        case SYNCHRONIZED:
        case TRANSIENT:
        case VOID:
        case VOLATILE:
        case IDENTIFIER:
        case LBRACE:
        case SEMICOLON:
        case AT:
        case LT:
          ;
          break;
        default:
          jj_la1[178] = jj_gen;
          break label_70;
        }
        ClassOrInterfaceBodyDeclaration(false);
      }
      break;
    default:
      jj_la1[179] = jj_gen;
      ;
    }
    jj_consume_token(RBRACE);
  }

  final public void TypeParameter() throws ParseException {
    jj_consume_token(IDENTIFIER);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case EXTENDS:
      TypeBound();
      break;
    default:
      jj_la1[180] = jj_gen;
      ;
    }
  }

  final public void ClassOrInterfaceType() throws ParseException {
    jj_consume_token(IDENTIFIER);
    if (jj_2_50(4)) {
      TypeArguments();
    } else {
      ;
    }
    label_71:
    while (true) {
      if (jj_2_51(2)) {
        ;
      } else {
        break label_71;
      }
      jj_consume_token(DOT);
      jj_consume_token(IDENTIFIER);
      if (jj_2_52(2)) {
        TypeArguments();
      } else {
        ;
      }
    }
  }

  final public void ClassOrInterfaceBodyDeclaration(boolean isInterface) throws ParseException {
   boolean isNestedInterface = false;
   int modifiers;
    if (jj_2_55(2)) {
      Initializer();
     if (isInterface)
        {if (true) throw new ParseException("An interface cannot have initializers");}
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ABSTRACT:
      case ASSERT:
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case CLASS:
      case DOUBLE:
      case ENUM:
      case FINAL:
      case FLOAT:
      case INT:
      case INTERFACE:
      case LONG:
      case NATIVE:
      case PRIVATE:
      case PROTECTED:
      case PUBLIC:
      case SHORT:
      case STATIC:
      case TESTAAAA:
      case SYNCHRONIZED:
      case TRANSIENT:
      case VOID:
      case VOLATILE:
      case IDENTIFIER:
      case AT:
      case LT:
        modifiers = Modifiers();
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case CLASS:
        case INTERFACE:
          ClassOrInterfaceDeclaration(modifiers);
          break;
        case ENUM:
          EnumDeclaration(modifiers);
          break;
        default:
          jj_la1[181] = jj_gen;
          if (jj_2_53(2147483647)) {
            ConstructorDeclaration();
          } else if (jj_2_54(2147483647)) {
            FieldDeclaration15(modifiers);
          } else {
            switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
            case ABSTRACT:
            case ASSERT:
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case DOUBLE:
            case ENUM:
            case FINAL:
            case FLOAT:
            case INT:
            case LONG:
            case NATIVE:
            case PRIVATE:
            case PROTECTED:
            case PUBLIC:
            case SHORT:
            case STATIC:
            case TESTAAAA:
            case SYNCHRONIZED:
            case VOID:
            case IDENTIFIER:
            case AT:
            case LT:
              MethodDeclaration15(modifiers);
              break;
            default:
              jj_la1[182] = jj_gen;
              jj_consume_token(-1);
              throw new ParseException();
            }
          }
        }
        break;
      case SEMICOLON:
        jj_consume_token(SEMICOLON);
        break;
      default:
        jj_la1[183] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

  final public void EnumConstant() throws ParseException {
    Modifiers();
    jj_consume_token(IDENTIFIER);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LPAREN:
      Arguments();
      break;
    default:
      jj_la1[184] = jj_gen;
      ;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LBRACE:
      ClassOrInterfaceBody(false);
      break;
    default:
      jj_la1[185] = jj_gen;
      ;
    }
  }

  final public void TypeBound() throws ParseException {
    jj_consume_token(EXTENDS);
    ClassOrInterfaceType();
    label_72:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case BIT_AND:
        ;
        break;
      default:
        jj_la1[186] = jj_gen;
        break label_72;
      }
      jj_consume_token(BIT_AND);
      ClassOrInterfaceType();
    }
  }

  final public void TypeArguments() throws ParseException {
    jj_consume_token(LT);
    TypeArgument();
    label_73:
    while (true) {
      if (jj_2_56(2)) {
        ;
      } else {
        break label_73;
      }
      jj_consume_token(COMMA);
      TypeArgument();
    }
    if (jj_2_57(3)) {
      jj_consume_token(GT);
    } else {
      ;
    }
    if (jj_2_58(3)) {
      jj_consume_token(RSIGNEDSHIFT);
    } else {
      ;
    }
    if (jj_2_59(3)) {
      jj_consume_token(RUNSIGNEDSHIFT);
    } else {
      ;
    }
  }

  final public void TypeArgument() throws ParseException {
    if (jj_2_60(2)) {
      jj_consume_token(IDENTIFIER);
      TypeArguments();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case DOUBLE:
      case FLOAT:
      case INT:
      case LONG:
      case SHORT:
      case IDENTIFIER:
        ReferenceType();
        break;
      case HOOK:
        jj_consume_token(HOOK);
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case EXTENDS:
        case SUPER:
          WildcardBounds();
          break;
        default:
          jj_la1[187] = jj_gen;
          ;
        }
        break;
      default:
        jj_la1[188] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

  final public void ReferenceType() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case FLOAT:
    case INT:
    case LONG:
    case SHORT:
      PrimitiveType();
      label_74:
      while (true) {
        jj_consume_token(LBRACKET);
        jj_consume_token(RBRACKET);
        if (jj_2_61(2)) {
          ;
        } else {
          break label_74;
        }
      }
      break;
    case IDENTIFIER:
      ClassOrInterfaceType();
      label_75:
      while (true) {
        if (jj_2_62(2)) {
          ;
        } else {
          break label_75;
        }
        jj_consume_token(LBRACKET);
        jj_consume_token(RBRACKET);
      }
      break;
    default:
      jj_la1[189] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void WildcardBounds() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case EXTENDS:
      jj_consume_token(EXTENDS);
      ReferenceType();
      break;
    case SUPER:
      jj_consume_token(SUPER);
      ReferenceType();
      break;
    default:
      jj_la1[190] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void FieldDeclaration15(int modifiers) throws ParseException {
    Type();
    VariableDeclarator();
    label_76:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case COMMA:
        ;
        break;
      default:
        jj_la1[191] = jj_gen;
        break label_76;
      }
      jj_consume_token(COMMA);
      VariableDeclarator();
    }
    jj_consume_token(SEMICOLON);
  }

  final public void MethodDeclaration15(int modifiers) throws ParseException {
    MethodDeclaration();
  }

  final public void MethodDeclarator15() throws ParseException {
    jj_consume_token(IDENTIFIER);
    FormalParameters();
    label_77:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LBRACKET:
        ;
        break;
      default:
        jj_la1[192] = jj_gen;
        break label_77;
      }
      jj_consume_token(LBRACKET);
      jj_consume_token(RBRACKET);
    }
  }

  final public void FormalParameters15() throws ParseException {
    jj_consume_token(LPAREN);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ABSTRACT:
    case ASSERT:
    case BOOLEAN:
    case BYTE:
    case CHAR:
    case DOUBLE:
    case ENUM:
    case FINAL:
    case FLOAT:
    case INT:
    case LONG:
    case NATIVE:
    case PRIVATE:
    case PROTECTED:
    case PUBLIC:
    case SHORT:
    case STATIC:
    case TESTAAAA:
    case SYNCHRONIZED:
    case TRANSIENT:
    case VOLATILE:
    case IDENTIFIER:
    case AT:
      FormalParameter15();
      label_78:
      while (true) {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case COMMA:
          ;
          break;
        default:
          jj_la1[193] = jj_gen;
          break label_78;
        }
        jj_consume_token(COMMA);
        FormalParameter15();
      }
      break;
    default:
      jj_la1[194] = jj_gen;
      ;
    }
    jj_consume_token(RPAREN);
  }

  final public void FormalParameter15() throws ParseException {
    Modifiers();
    Type();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ELLIPSIS:
      jj_consume_token(ELLIPSIS);
      break;
    default:
      jj_la1[195] = jj_gen;
      ;
    }
    VariableDeclaratorId();
  }

  final public void MemberSelector() throws ParseException {
    jj_consume_token(DOT);
    TypeArguments();
    jj_consume_token(IDENTIFIER);
  }

  final private boolean jj_2_1(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_1();
    jj_save(0, xla);
    return retval;
  }

  final private boolean jj_2_2(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_2();
    jj_save(1, xla);
    return retval;
  }

  final private boolean jj_2_3(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_3();
    jj_save(2, xla);
    return retval;
  }

  final private boolean jj_2_4(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_4();
    jj_save(3, xla);
    return retval;
  }

  final private boolean jj_2_5(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_5();
    jj_save(4, xla);
    return retval;
  }

  final private boolean jj_2_6(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_6();
    jj_save(5, xla);
    return retval;
  }

  final private boolean jj_2_7(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_7();
    jj_save(6, xla);
    return retval;
  }

  final private boolean jj_2_8(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_8();
    jj_save(7, xla);
    return retval;
  }

  final private boolean jj_2_9(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_9();
    jj_save(8, xla);
    return retval;
  }

  final private boolean jj_2_10(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_10();
    jj_save(9, xla);
    return retval;
  }

  final private boolean jj_2_11(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_11();
    jj_save(10, xla);
    return retval;
  }

  final private boolean jj_2_12(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_12();
    jj_save(11, xla);
    return retval;
  }

  final private boolean jj_2_13(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_13();
    jj_save(12, xla);
    return retval;
  }

  final private boolean jj_2_14(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_14();
    jj_save(13, xla);
    return retval;
  }

  final private boolean jj_2_15(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_15();
    jj_save(14, xla);
    return retval;
  }

  final private boolean jj_2_16(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_16();
    jj_save(15, xla);
    return retval;
  }

  final private boolean jj_2_17(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_17();
    jj_save(16, xla);
    return retval;
  }

  final private boolean jj_2_18(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_18();
    jj_save(17, xla);
    return retval;
  }

  final private boolean jj_2_19(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_19();
    jj_save(18, xla);
    return retval;
  }

  final private boolean jj_2_20(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_20();
    jj_save(19, xla);
    return retval;
  }

  final private boolean jj_2_21(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_21();
    jj_save(20, xla);
    return retval;
  }

  final private boolean jj_2_22(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_22();
    jj_save(21, xla);
    return retval;
  }

  final private boolean jj_2_23(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_23();
    jj_save(22, xla);
    return retval;
  }

  final private boolean jj_2_24(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_24();
    jj_save(23, xla);
    return retval;
  }

  final private boolean jj_2_25(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_25();
    jj_save(24, xla);
    return retval;
  }

  final private boolean jj_2_26(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_26();
    jj_save(25, xla);
    return retval;
  }

  final private boolean jj_2_27(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_27();
    jj_save(26, xla);
    return retval;
  }

  final private boolean jj_2_28(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_28();
    jj_save(27, xla);
    return retval;
  }

  final private boolean jj_2_29(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_29();
    jj_save(28, xla);
    return retval;
  }

  final private boolean jj_2_30(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_30();
    jj_save(29, xla);
    return retval;
  }

  final private boolean jj_2_31(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_31();
    jj_save(30, xla);
    return retval;
  }

  final private boolean jj_2_32(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_32();
    jj_save(31, xla);
    return retval;
  }

  final private boolean jj_2_33(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_33();
    jj_save(32, xla);
    return retval;
  }

  final private boolean jj_2_34(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_34();
    jj_save(33, xla);
    return retval;
  }

  final private boolean jj_2_35(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_35();
    jj_save(34, xla);
    return retval;
  }

  final private boolean jj_2_36(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_36();
    jj_save(35, xla);
    return retval;
  }

  final private boolean jj_2_37(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_37();
    jj_save(36, xla);
    return retval;
  }

  final private boolean jj_2_38(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_38();
    jj_save(37, xla);
    return retval;
  }

  final private boolean jj_2_39(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_39();
    jj_save(38, xla);
    return retval;
  }

  final private boolean jj_2_40(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_40();
    jj_save(39, xla);
    return retval;
  }

  final private boolean jj_2_41(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_41();
    jj_save(40, xla);
    return retval;
  }

  final private boolean jj_2_42(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_42();
    jj_save(41, xla);
    return retval;
  }

  final private boolean jj_2_43(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_43();
    jj_save(42, xla);
    return retval;
  }

  final private boolean jj_2_44(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_44();
    jj_save(43, xla);
    return retval;
  }

  final private boolean jj_2_45(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_45();
    jj_save(44, xla);
    return retval;
  }

  final private boolean jj_2_46(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_46();
    jj_save(45, xla);
    return retval;
  }

  final private boolean jj_2_47(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_47();
    jj_save(46, xla);
    return retval;
  }

  final private boolean jj_2_48(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_48();
    jj_save(47, xla);
    return retval;
  }

  final private boolean jj_2_49(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_49();
    jj_save(48, xla);
    return retval;
  }

  final private boolean jj_2_50(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_50();
    jj_save(49, xla);
    return retval;
  }

  final private boolean jj_2_51(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_51();
    jj_save(50, xla);
    return retval;
  }

  final private boolean jj_2_52(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_52();
    jj_save(51, xla);
    return retval;
  }

  final private boolean jj_2_53(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_53();
    jj_save(52, xla);
    return retval;
  }

  final private boolean jj_2_54(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_54();
    jj_save(53, xla);
    return retval;
  }

  final private boolean jj_2_55(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_55();
    jj_save(54, xla);
    return retval;
  }

  final private boolean jj_2_56(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_56();
    jj_save(55, xla);
    return retval;
  }

  final private boolean jj_2_57(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_57();
    jj_save(56, xla);
    return retval;
  }

  final private boolean jj_2_58(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_58();
    jj_save(57, xla);
    return retval;
  }

  final private boolean jj_2_59(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_59();
    jj_save(58, xla);
    return retval;
  }

  final private boolean jj_2_60(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_60();
    jj_save(59, xla);
    return retval;
  }

  final private boolean jj_2_61(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_61();
    jj_save(60, xla);
    return retval;
  }

  final private boolean jj_2_62(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_62();
    jj_save(61, xla);
    return retval;
  }

  final private boolean jj_3R_461() {
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_565() {
    if (jj_scan_token(INCR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_556() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_565()) {
    jj_scanpos = xsp;
    if (jj_3R_566()) {
    jj_scanpos = xsp;
    if (jj_3R_567()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_412() {
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_403()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_461()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_364() {
    if (jj_3R_96()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_556()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_112() {
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_363() {
    if (jj_3R_371()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_3() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(ENUM)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_362() {
    if (jj_3R_370()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_345() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_362()) {
    jj_scanpos = xsp;
    if (jj_3R_363()) {
    jj_scanpos = xsp;
    if (jj_3R_364()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_80() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_2() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_80()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_81()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(CLASS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_111() {
    if (jj_scan_token(ENUM)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_344() {
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_319() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_302() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_319()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_418()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_517()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3_41() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_111()) {
    jj_scanpos = xsp;
    if (jj_3R_112()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_113()) {
    jj_scanpos = xsp;
    if (jj_3R_114()) {
    jj_scanpos = xsp;
    if (jj_3R_115()) {
    jj_scanpos = xsp;
    if (jj_3R_116()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_288() {
    if (jj_3R_305()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_287() {
    if (jj_3R_304()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_286() {
    if (jj_3R_303()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_272() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_285()) {
    jj_scanpos = xsp;
    if (jj_3R_286()) {
    jj_scanpos = xsp;
    if (jj_3R_287()) {
    jj_scanpos = xsp;
    if (jj_3R_288()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_285() {
    if (jj_3R_302()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_235() {
    if (jj_3R_272()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_150() {
    if (jj_scan_token(LBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_235()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(RBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_536() {
    if (jj_scan_token(COLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_343() {
    if (jj_scan_token(ASSERT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_536()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_110() {
    if (jj_scan_token(ASSERT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_109() {
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(COLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_303()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_334() {
    if (jj_3R_356()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_413() {
    if (jj_scan_token(THROWS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_450()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_333() {
    if (jj_3R_355()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_332() {
    if (jj_3R_354()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_415() {
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_331() {
    if (jj_3R_353()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_330() {
    if (jj_3R_352()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_329() {
    if (jj_3R_351()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_328() {
    if (jj_3R_350()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_37() {
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_414() {
    if (jj_3R_150()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_327() {
    if (jj_3R_349()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_326() {
    if (jj_3R_348()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_325() {
    if (jj_3R_347()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_324() {
    if (jj_3R_346()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_40() {
    if (jj_3R_110()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_323() {
    if (jj_3R_345()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_322() {
    if (jj_3R_344()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_321() {
    if (jj_3R_150()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_320() {
    if (jj_3R_343()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_59() {
    if (jj_scan_token(RUNSIGNEDSHIFT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_411() {
    if (jj_3R_168()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_39() {
    if (jj_3R_109()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_303() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_39()) {
    jj_scanpos = xsp;
    if (jj_3R_320()) {
    jj_scanpos = xsp;
    if (jj_3R_321()) {
    jj_scanpos = xsp;
    if (jj_3R_322()) {
    jj_scanpos = xsp;
    if (jj_3R_323()) {
    jj_scanpos = xsp;
    if (jj_3R_324()) {
    jj_scanpos = xsp;
    if (jj_3R_325()) {
    jj_scanpos = xsp;
    if (jj_3R_326()) {
    jj_scanpos = xsp;
    if (jj_3R_327()) {
    jj_scanpos = xsp;
    if (jj_3R_328()) {
    jj_scanpos = xsp;
    if (jj_3R_329()) {
    jj_scanpos = xsp;
    if (jj_3R_330()) {
    jj_scanpos = xsp;
    if (jj_3R_331()) {
    jj_scanpos = xsp;
    if (jj_3R_332()) {
    jj_scanpos = xsp;
    if (jj_3R_333()) {
    jj_scanpos = xsp;
    if (jj_3R_334()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_410() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_357() {
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_337() {
    Token xsp;
    if (jj_3R_357()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_357()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_3R_248()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_36() {
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_58() {
    if (jj_scan_token(RSIGNEDSHIFT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_309() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_38()) {
    jj_scanpos = xsp;
    if (jj_3R_337()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_38() {
    Token xsp;
    if (jj_3_36()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_36()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_37()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_460() {
    if (jj_scan_token(TESTAAAA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_79() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_107() {
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_136()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_1() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_79()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(PACKAGE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_459() {
    if (jj_scan_token(SYNCHRONIZED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_366() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_555() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_418()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_338() {
    if (jj_3R_358()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_458() {
    if (jj_scan_token(NATIVE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_57() {
    if (jj_scan_token(GT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_457() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_312() {
    if (jj_3R_264()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_338()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_456() {
    if (jj_scan_token(ABSTRACT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_311() {
    if (jj_3R_309()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_62() {
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_224() {
    if (jj_scan_token(NEW)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_90()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_310()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_311()) {
    jj_scanpos = xsp;
    if (jj_3R_312()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_455() {
    if (jj_scan_token(STATIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_310() {
    if (jj_3R_136()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_535() {
    if (jj_3R_390()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_454() {
    if (jj_scan_token(PRIVATE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_359() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_106() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_35()) {
    jj_scanpos = xsp;
    if (jj_3R_224()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_35() {
    if (jj_scan_token(NEW)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_102()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_309()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_141() {
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_453() {
    if (jj_scan_token(PROTECTED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_61() {
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_534() {
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_418()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_555()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_140() {
    if (jj_scan_token(ASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_502() {
    if (jj_3R_514()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_306() {
    if (jj_scan_token(BIT_AND)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_300()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_452() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_409() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_452()) {
    jj_scanpos = xsp;
    if (jj_3R_453()) {
    jj_scanpos = xsp;
    if (jj_3R_454()) {
    jj_scanpos = xsp;
    if (jj_3R_455()) {
    jj_scanpos = xsp;
    if (jj_3R_456()) {
    jj_scanpos = xsp;
    if (jj_3R_457()) {
    jj_scanpos = xsp;
    if (jj_3R_458()) {
    jj_scanpos = xsp;
    if (jj_3R_459()) {
    jj_scanpos = xsp;
    if (jj_3R_460()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_316() {
    if (jj_scan_token(SUPER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_268()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_315() {
    if (jj_scan_token(EXTENDS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_268()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_299() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_315()) {
    jj_scanpos = xsp;
    if (jj_3R_316()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_296() {
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_359()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_139() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_279() {
    if (jj_3R_296()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_56() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_142()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_408() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_283() {
    if (jj_3R_300()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_62()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3_15() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_94()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_268() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_282()) {
    jj_scanpos = xsp;
    if (jj_3R_283()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_282() {
    if (jj_3R_102()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    if (jj_3_61()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_61()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_264() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_279()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_281() {
    if (jj_3R_299()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_501() {
    if (jj_3R_264()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_390() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_408()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_409()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_410()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    xsp = jj_scanpos;
    if (jj_3R_411()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_105()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_412()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_413()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_414()) {
    jj_scanpos = xsp;
    if (jj_3R_415()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_138() {
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_231() {
    if (jj_scan_token(HOOK)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_281()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_308() {
    if (jj_scan_token(NULL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_230() {
    if (jj_3R_268()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_419() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_418()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_142() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_60()) {
    jj_scanpos = xsp;
    if (jj_3R_230()) {
    jj_scanpos = xsp;
    if (jj_3R_231()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_60() {
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_136()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_489() {
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_336() {
    if (jj_scan_token(FALSE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_335() {
    if (jj_scan_token(TRUE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_307() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_335()) {
    jj_scanpos = xsp;
    if (jj_3R_336()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_136() {
    if (jj_scan_token(LT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_142()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_56()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    xsp = jj_scanpos;
    if (jj_3_57()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3_58()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3_59()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_365() {
    if (jj_3R_94()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_15()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_295() {
    if (jj_3R_308()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_470() {
    if (jj_scan_token(ASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_94()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_289() {
    if (jj_scan_token(EXTENDS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_300()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_306()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_294() {
    if (jj_3R_307()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_188() {
    if (jj_scan_token(TESTAAAA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_293() {
    if (jj_scan_token(STRING_LITERAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_54() {
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_138()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    xsp = jj_scanpos;
    if (jj_3R_139()) {
    jj_scanpos = xsp;
    if (jj_3R_140()) {
    jj_scanpos = xsp;
    if (jj_3R_141()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_488() {
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_248() {
    if (jj_scan_token(LBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_365()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_366()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_292() {
    if (jj_scan_token(CHARACTER_LITERAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_137() {
    if (jj_3R_168()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_181() {
    if (jj_scan_token(TESTAAAA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_291() {
    if (jj_scan_token(FLOATING_POINT_LITERAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_135() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_501()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_502()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_53() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_137()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_277() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_290()) {
    jj_scanpos = xsp;
    if (jj_3R_291()) {
    jj_scanpos = xsp;
    if (jj_3R_292()) {
    jj_scanpos = xsp;
    if (jj_3R_293()) {
    jj_scanpos = xsp;
    if (jj_3R_294()) {
    jj_scanpos = xsp;
    if (jj_3R_295()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_290() {
    if (jj_scan_token(INTEGER_LITERAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_190() {
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_52() {
    if (jj_3R_136()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_529() {
    if (jj_3R_535()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_487() {
    if (jj_scan_token(ENUM)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_516() {
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_469() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_487()) {
    jj_scanpos = xsp;
    if (jj_3R_488()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_489()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_468() {
    if (jj_scan_token(VOLATILE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_94() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_189()) {
    jj_scanpos = xsp;
    if (jj_3R_190()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_189() {
    if (jj_3R_248()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_528() {
    if (jj_3R_534()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_187() {
    if (jj_scan_token(PRIVATE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_221() {
    if (jj_3R_264()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_527() {
    if (jj_3R_389()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_180() {
    if (jj_scan_token(PRIVATE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_220() {
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_526() {
    if (jj_3R_387()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_219() {
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_525() {
    if (jj_3R_533()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_34() {
    if (jj_3R_107()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_418() {
    if (jj_3R_469()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_470()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_29() {
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SUPER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_467() {
    if (jj_scan_token(TRANSIENT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_33() {
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_106()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_262() {
    if (jj_3R_277()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_515() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_525()) {
    jj_scanpos = xsp;
    if (jj_3R_526()) {
    jj_scanpos = xsp;
    if (jj_3R_527()) {
    jj_scanpos = xsp;
    if (jj_3R_528()) {
    jj_scanpos = xsp;
    if (jj_3R_529()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_186() {
    if (jj_scan_token(PROTECTED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_104() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_32()) {
    jj_scanpos = xsp;
    if (jj_3_33()) {
    jj_scanpos = xsp;
    if (jj_3_34()) {
    jj_scanpos = xsp;
    if (jj_3R_219()) {
    jj_scanpos = xsp;
    if (jj_3R_220()) {
    jj_scanpos = xsp;
    if (jj_3R_221()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_32() {
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(THIS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_417() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_50() {
    if (jj_3R_136()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_179() {
    if (jj_scan_token(PROTECTED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_31() {
    if (jj_3R_105()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(CLASS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_254() {
    if (jj_3R_90()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_29()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_261() {
    if (jj_scan_token(NEW)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_273() {
    if (jj_3R_289()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_503() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_55()) {
    jj_scanpos = xsp;
    if (jj_3R_515()) {
    jj_scanpos = xsp;
    if (jj_3R_516()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_55() {
    if (jj_3R_83()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_466() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_185() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_49() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_135()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_178() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_51() {
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_52()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_260() {
    if (jj_scan_token(SUPER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_253() {
    if (jj_3R_105()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(CLASS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_276() {
    if (jj_scan_token(GT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_300() {
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_50()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_51()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_465() {
    if (jj_scan_token(STATIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_252() {
    if (jj_3R_106()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_480() {
    if (jj_3R_503()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_184() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_236() {
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_273()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_251() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_275() {
    if (jj_scan_token(RSIGNEDSHIFT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_444() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_177() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_28() {
    if (jj_3R_104()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_259() {
    if (jj_scan_token(THIS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_445() {
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_480()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3_30() {
    if (jj_scan_token(SUPER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_238() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_274()) {
    jj_scanpos = xsp;
    if (jj_3R_275()) {
    jj_scanpos = xsp;
    if (jj_3R_276()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_274() {
    if (jj_scan_token(RUNSIGNEDSHIFT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_443() {
    if (jj_3R_135()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_49()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_578() {
    if (jj_scan_token(DECR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_464() {
    if (jj_scan_token(PRIVATE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_463() {
    if (jj_scan_token(PROTECTED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_462() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_416() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_462()) {
    jj_scanpos = xsp;
    if (jj_3R_463()) {
    jj_scanpos = xsp;
    if (jj_3R_464()) {
    jj_scanpos = xsp;
    if (jj_3R_465()) {
    jj_scanpos = xsp;
    if (jj_3R_466()) {
    jj_scanpos = xsp;
    if (jj_3R_467()) {
    jj_scanpos = xsp;
    if (jj_3R_468()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_250() {
    if (jj_scan_token(THIS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_183() {
    if (jj_scan_token(ABSTRACT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_392() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_416()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_417()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_418()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_419()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_400() {
    if (jj_scan_token(LBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_443()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_444()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_445()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_191() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_249()) {
    jj_scanpos = xsp;
    if (jj_3R_250()) {
    jj_scanpos = xsp;
    if (jj_3_30()) {
    jj_scanpos = xsp;
    if (jj_3R_251()) {
    jj_scanpos = xsp;
    if (jj_3R_252()) {
    jj_scanpos = xsp;
    if (jj_3R_253()) {
    jj_scanpos = xsp;
    if (jj_3R_254()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_249() {
    if (jj_3R_277()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_577() {
    if (jj_scan_token(INCR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_572() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_577()) {
    jj_scanpos = xsp;
    if (jj_3R_578()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_14() {
    if (jj_3R_91()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_176() {
    if (jj_scan_token(ABSTRACT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_258() {
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_96() {
    if (jj_3R_191()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_28()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_524() {
    if (jj_3R_503()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_27() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_102()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_13() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(ENUM)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_500() {
    if (jj_3R_392()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_93() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_182()) {
    jj_scanpos = xsp;
    if (jj_3R_183()) {
    jj_scanpos = xsp;
    if (jj_3R_184()) {
    jj_scanpos = xsp;
    if (jj_3R_185()) {
    jj_scanpos = xsp;
    if (jj_3R_186()) {
    jj_scanpos = xsp;
    if (jj_3R_187()) {
    jj_scanpos = xsp;
    if (jj_3R_188()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_182() {
    if (jj_scan_token(STATIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_514() {
    if (jj_scan_token(LBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_524()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(RBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_257() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_12() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_93()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(INTERFACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_499() {
    if (jj_3R_390()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_92() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_175()) {
    jj_scanpos = xsp;
    if (jj_3R_176()) {
    jj_scanpos = xsp;
    if (jj_3R_177()) {
    jj_scanpos = xsp;
    if (jj_3R_178()) {
    jj_scanpos = xsp;
    if (jj_3R_179()) {
    jj_scanpos = xsp;
    if (jj_3R_180()) {
    jj_scanpos = xsp;
    if (jj_3R_181()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_175() {
    if (jj_scan_token(STATIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_563() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_520()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_11() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_92()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(CLASS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_26() {
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_256() {
    if (jj_scan_token(BANG)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_237() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_236()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_562() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_490()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_548() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_562()) {
    jj_scanpos = xsp;
    if (jj_3R_563()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_479() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_300()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_498() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_387()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_440() {
    if (jj_scan_token(EXTENDS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_450()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_442() {
    if (jj_scan_token(IMPLEMENTS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_300()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_479()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_497() {
    if (jj_3R_386()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_255() {
    if (jj_scan_token(TILDE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_549() {
    if (jj_3R_96()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_572()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_496() {
    if (jj_3R_385()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_495() {
    if (jj_3R_344()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_478() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_495()) {
    jj_scanpos = xsp;
    if (jj_3R_496()) {
    jj_scanpos = xsp;
    if (jj_3R_497()) {
    jj_scanpos = xsp;
    if (jj_3R_498()) {
    jj_scanpos = xsp;
    if (jj_3R_499()) {
    jj_scanpos = xsp;
    if (jj_3R_500()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_573() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_300()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_564() {
    if (jj_scan_token(EXTENDS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_300()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_573()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3_25() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_168() {
    if (jj_scan_token(LT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_236()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_237()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    xsp = jj_scanpos;
    if (jj_3R_238()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_439() {
    if (jj_3R_168()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_207() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_255()) {
    jj_scanpos = xsp;
    if (jj_3R_256()) {
    jj_scanpos = xsp;
    if (jj_3R_257()) {
    jj_scanpos = xsp;
    if (jj_3R_258()) {
    jj_scanpos = xsp;
    if (jj_3R_259()) {
    jj_scanpos = xsp;
    if (jj_3R_260()) {
    jj_scanpos = xsp;
    if (jj_3R_261()) {
    jj_scanpos = xsp;
    if (jj_3R_262()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_206() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_101() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_24()) {
    jj_scanpos = xsp;
    if (jj_3R_206()) {
    jj_scanpos = xsp;
    if (jj_3R_207()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_24() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_102()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_23() {
    if (jj_3R_101()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_441() {
    if (jj_3R_478()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_547() {
    if (jj_scan_token(BANG)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_532() {
    if (jj_3R_549()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_531() {
    if (jj_3R_548()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_523() {
    if (jj_scan_token(REM)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_546() {
    if (jj_scan_token(TILDE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_513() {
    if (jj_scan_token(MINUS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_305() {
    if (jj_scan_token(INTERFACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_439()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_440()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_441()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(RBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_530() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_546()) {
    jj_scanpos = xsp;
    if (jj_3R_547()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_490()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_520() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_530()) {
    jj_scanpos = xsp;
    if (jj_3R_531()) {
    jj_scanpos = xsp;
    if (jj_3R_532()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_522() {
    if (jj_scan_token(SLASH)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_494() {
    if (jj_scan_token(RUNSIGNEDSHIFT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_512() {
    if (jj_scan_token(PLUS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_371() {
    if (jj_scan_token(DECR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_96()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_491() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_512()) {
    jj_scanpos = xsp;
    if (jj_3R_513()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_471()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_476() {
    if (jj_scan_token(GE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_521() {
    if (jj_scan_token(STAR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_511() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_521()) {
    jj_scanpos = xsp;
    if (jj_3R_522()) {
    jj_scanpos = xsp;
    if (jj_3R_523()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_490()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_493() {
    if (jj_scan_token(RSIGNEDSHIFT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_370() {
    if (jj_scan_token(INCR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_96()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_475() {
    if (jj_scan_token(LE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_519() {
    if (jj_scan_token(MINUS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_492() {
    if (jj_scan_token(LSHIFT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_399() {
    if (jj_3R_442()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_510() {
    if (jj_3R_520()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_472() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_492()) {
    jj_scanpos = xsp;
    if (jj_3R_493()) {
    jj_scanpos = xsp;
    if (jj_3R_494()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_420()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_509() {
    if (jj_3R_371()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_474() {
    if (jj_scan_token(GT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_518() {
    if (jj_scan_token(PLUS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_508() {
    if (jj_3R_370()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_205() {
    if (jj_scan_token(ORASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_507() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_518()) {
    jj_scanpos = xsp;
    if (jj_3R_519()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_490()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_490() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_507()) {
    jj_scanpos = xsp;
    if (jj_3R_508()) {
    jj_scanpos = xsp;
    if (jj_3R_509()) {
    jj_scanpos = xsp;
    if (jj_3R_510()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_434() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_473() {
    if (jj_scan_token(LT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_396() {
    if (jj_scan_token(NE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_387() {
    if (jj_scan_token(ENUM)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_399()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_400()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_421() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_473()) {
    jj_scanpos = xsp;
    if (jj_3R_474()) {
    jj_scanpos = xsp;
    if (jj_3R_475()) {
    jj_scanpos = xsp;
    if (jj_3R_476()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_393()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_394() {
    if (jj_scan_token(INSTANCEOF)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_204() {
    if (jj_scan_token(XORASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_471() {
    if (jj_3R_490()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_511()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_395() {
    if (jj_scan_token(EQ)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_438() {
    if (jj_scan_token(TESTAAAA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_383() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_395()) {
    jj_scanpos = xsp;
    if (jj_3R_396()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_373()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_551() {
    if (jj_scan_token(INTERFACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_420() {
    if (jj_3R_471()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_491()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_203() {
    if (jj_scan_token(ANDASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_433() {
    if (jj_scan_token(ABSTRACT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_554() {
    if (jj_3R_442()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_393() {
    if (jj_3R_420()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_472()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_553() {
    if (jj_3R_564()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_374() {
    if (jj_scan_token(BIT_AND)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_368()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_552() {
    if (jj_3R_168()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_202() {
    if (jj_scan_token(RUNSIGNEDSHIFTASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_550() {
    if (jj_scan_token(CLASS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_437() {
    if (jj_scan_token(PRIVATE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_436() {
    if (jj_scan_token(PROTECTED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_533() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_550()) {
    jj_scanpos = xsp;
    if (jj_3R_551()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_552()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_553()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_554()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_514()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_435() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_382() {
    if (jj_3R_393()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_421()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_432() {
    if (jj_scan_token(STATIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_398() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_432()) {
    jj_scanpos = xsp;
    if (jj_3R_433()) {
    jj_scanpos = xsp;
    if (jj_3R_434()) {
    jj_scanpos = xsp;
    if (jj_3R_435()) {
    jj_scanpos = xsp;
    if (jj_3R_436()) {
    jj_scanpos = xsp;
    if (jj_3R_437()) {
    jj_scanpos = xsp;
    if (jj_3R_438()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_386() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_398()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_3R_305()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_361() {
    if (jj_scan_token(BIT_OR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_339()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_201() {
    if (jj_scan_token(RSIGNEDSHIFTASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_373() {
    if (jj_3R_382()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_394()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_369() {
    if (jj_scan_token(XOR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_360()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_247() {
    if (jj_scan_token(TESTAAAA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_340() {
    if (jj_scan_token(SC_AND)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_313()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_368() {
    if (jj_3R_373()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_383()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_314() {
    if (jj_scan_token(SC_OR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_297()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_200() {
    if (jj_scan_token(LSHIFTASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_134() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_360() {
    if (jj_3R_368()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_374()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_133() {
    if (jj_scan_token(TESTAAAA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_199() {
    if (jj_scan_token(MINUSASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_298() {
    if (jj_scan_token(HOOK)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(COLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_266()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_132() {
    if (jj_scan_token(VOLATILE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_339() {
    if (jj_3R_360()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_369()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_246() {
    if (jj_scan_token(SYNCHRONIZED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_198() {
    if (jj_scan_token(PLUSASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_131() {
    if (jj_scan_token(TRANSIENT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_313() {
    if (jj_3R_339()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_361()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_130() {
    if (jj_scan_token(NATIVE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_197() {
    if (jj_scan_token(REMASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_245() {
    if (jj_scan_token(NATIVE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_297() {
    if (jj_3R_313()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_340()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_129() {
    if (jj_scan_token(SYNCHRONIZED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_157() {
    if (jj_scan_token(TESTAAAA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_196() {
    if (jj_scan_token(SLASHASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_280() {
    if (jj_3R_297()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_314()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_128() {
    if (jj_scan_token(ABSTRACT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_244() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_195() {
    if (jj_scan_token(STARASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_266() {
    if (jj_3R_280()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_298()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_127() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_156() {
    if (jj_scan_token(PRIVATE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_164() {
    if (jj_scan_token(TESTAAAA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_89() {
    if (jj_3R_168()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_126() {
    if (jj_scan_token(PRIVATE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_100() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_194()) {
    jj_scanpos = xsp;
    if (jj_3R_195()) {
    jj_scanpos = xsp;
    if (jj_3R_196()) {
    jj_scanpos = xsp;
    if (jj_3R_197()) {
    jj_scanpos = xsp;
    if (jj_3R_198()) {
    jj_scanpos = xsp;
    if (jj_3R_199()) {
    jj_scanpos = xsp;
    if (jj_3R_200()) {
    jj_scanpos = xsp;
    if (jj_3R_201()) {
    jj_scanpos = xsp;
    if (jj_3R_202()) {
    jj_scanpos = xsp;
    if (jj_3R_203()) {
    jj_scanpos = xsp;
    if (jj_3R_204()) {
    jj_scanpos = xsp;
    if (jj_3R_205()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_194() {
    if (jj_scan_token(ASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_125() {
    if (jj_scan_token(PROTECTED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_243() {
    if (jj_scan_token(ABSTRACT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_22() {
    if (jj_3R_96()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_100()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_265() {
    if (jj_3R_96()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_100()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_124() {
    if (jj_scan_token(STATIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_163() {
    if (jj_scan_token(PRIVATE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_155() {
    if (jj_scan_token(PROTECTED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_226() {
    if (jj_3R_266()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_167() {
    if (jj_scan_token(PRIVATE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_505() {
    if (jj_3R_136()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_123() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_242() {
    if (jj_scan_token(STATIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_108() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_225()) {
    jj_scanpos = xsp;
    if (jj_3R_226()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_225() {
    if (jj_3R_265()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_48() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_123()) {
    jj_scanpos = xsp;
    if (jj_3R_124()) {
    jj_scanpos = xsp;
    if (jj_3R_125()) {
    jj_scanpos = xsp;
    if (jj_3R_126()) {
    jj_scanpos = xsp;
    if (jj_3R_127()) {
    jj_scanpos = xsp;
    if (jj_3R_128()) {
    jj_scanpos = xsp;
    if (jj_3R_129()) {
    jj_scanpos = xsp;
    if (jj_3R_130()) {
    jj_scanpos = xsp;
    if (jj_3R_131()) {
    jj_scanpos = xsp;
    if (jj_3R_132()) {
    jj_scanpos = xsp;
    if (jj_3R_133()) {
    jj_scanpos = xsp;
    if (jj_3R_134()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_483() {
    if (jj_3R_136()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_82() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_48()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_154() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_162() {
    if (jj_scan_token(PROTECTED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_484() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_90()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_505()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_98() {
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_166() {
    if (jj_scan_token(PROTECTED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_450() {
    if (jj_3R_90()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_483()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_484()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_241() {
    if (jj_scan_token(PRIVATE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_153() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_97() {
    if (jj_scan_token(ENUM)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_342() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_161() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_21() {
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_88() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_165()) {
    jj_scanpos = xsp;
    if (jj_3R_166()) {
    jj_scanpos = xsp;
    if (jj_3R_167()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_165() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_240() {
    if (jj_scan_token(PROTECTED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_160() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_152() {
    if (jj_scan_token(ABSTRACT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_170() {
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_20() {
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_97()) {
    jj_scanpos = xsp;
    if (jj_3R_98()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_47() {
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_173() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_172() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_239()) {
    jj_scanpos = xsp;
    if (jj_3R_240()) {
    jj_scanpos = xsp;
    if (jj_3R_241()) {
    jj_scanpos = xsp;
    if (jj_3R_242()) {
    jj_scanpos = xsp;
    if (jj_3R_243()) {
    jj_scanpos = xsp;
    if (jj_3R_244()) {
    jj_scanpos = xsp;
    if (jj_3R_245()) {
    jj_scanpos = xsp;
    if (jj_3R_246()) {
    jj_scanpos = xsp;
    if (jj_3R_247()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_174() {
    if (jj_3R_168()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_239() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_171() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_87() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_91() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_171()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_172()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_173()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    xsp = jj_scanpos;
    if (jj_3R_174()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_105()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_9() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_87()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    xsp = jj_scanpos;
    if (jj_3R_88()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_89()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_90()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_151() {
    if (jj_scan_token(STATIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_85() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_151()) {
    jj_scanpos = xsp;
    if (jj_3R_152()) {
    jj_scanpos = xsp;
    if (jj_3R_153()) {
    jj_scanpos = xsp;
    if (jj_3R_154()) {
    jj_scanpos = xsp;
    if (jj_3R_155()) {
    jj_scanpos = xsp;
    if (jj_3R_156()) {
    jj_scanpos = xsp;
    if (jj_3R_157()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_169() {
    if (jj_scan_token(ENUM)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_159() {
    if (jj_scan_token(ABSTRACT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_10() {
    if (jj_3R_91()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_90() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_169()) {
    jj_scanpos = xsp;
    if (jj_3R_170()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_20()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_391() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_388() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_381() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_391()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_3R_392()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_379() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_388()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_3R_389()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_8() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(ENUM)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_380() {
    if (jj_3R_390()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_158() {
    if (jj_scan_token(STATIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_86() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_158()) {
    jj_scanpos = xsp;
    if (jj_3R_159()) {
    jj_scanpos = xsp;
    if (jj_3R_160()) {
    jj_scanpos = xsp;
    if (jj_3R_161()) {
    jj_scanpos = xsp;
    if (jj_3R_162()) {
    jj_scanpos = xsp;
    if (jj_3R_163()) {
    jj_scanpos = xsp;
    if (jj_3R_164()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_223() {
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_7() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_86()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(INTERFACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_46() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_122()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_105() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_222()) {
    jj_scanpos = xsp;
    if (jj_3R_223()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_222() {
    if (jj_scan_token(VOID)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_84() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_6() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_84()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_85()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(CLASS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_384() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_378() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_387()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_215() {
    if (jj_scan_token(DOUBLE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_214() {
    if (jj_scan_token(FLOAT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_377() {
    if (jj_3R_386()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_213() {
    if (jj_scan_token(LONG)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_212() {
    if (jj_scan_token(INT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_376() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_384()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_3R_385()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_211() {
    if (jj_scan_token(SHORT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_341() {
    if (jj_3R_122()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_210() {
    if (jj_scan_token(BYTE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_5() {
    if (jj_3R_83()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_209() {
    if (jj_scan_token(CHAR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_375() {
    if (jj_3R_344()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_372() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_375()) {
    jj_scanpos = xsp;
    if (jj_3_5()) {
    jj_scanpos = xsp;
    if (jj_3R_376()) {
    jj_scanpos = xsp;
    if (jj_3R_377()) {
    jj_scanpos = xsp;
    if (jj_3R_378()) {
    jj_scanpos = xsp;
    if (jj_3R_379()) {
    jj_scanpos = xsp;
    if (jj_3R_380()) {
    jj_scanpos = xsp;
    if (jj_3R_381()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_278() {
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_102() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_208()) {
    jj_scanpos = xsp;
    if (jj_3R_209()) {
    jj_scanpos = xsp;
    if (jj_3R_210()) {
    jj_scanpos = xsp;
    if (jj_3R_211()) {
    jj_scanpos = xsp;
    if (jj_3R_212()) {
    jj_scanpos = xsp;
    if (jj_3R_213()) {
    jj_scanpos = xsp;
    if (jj_3R_214()) {
    jj_scanpos = xsp;
    if (jj_3R_215()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_208() {
    if (jj_scan_token(BOOLEAN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_267() {
    if (jj_scan_token(LBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_341()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_46()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    xsp = jj_scanpos;
    if (jj_3R_342()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_318() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_317()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_121() {
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_229() {
    if (jj_3R_266()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_228() {
    if (jj_3R_267()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_122() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_227()) {
    jj_scanpos = xsp;
    if (jj_3R_228()) {
    jj_scanpos = xsp;
    if (jj_3R_229()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_227() {
    if (jj_3R_143()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_431() {
    if (jj_scan_token(IMPLEMENTS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_450()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_424() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_317() {
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(ASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_122()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_217() {
    if (jj_3R_90()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_263()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_218() {
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_263() {
    if (jj_3R_136()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_278()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_216() {
    if (jj_3R_102()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_301() {
    if (jj_3R_317()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_318()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_284() {
    if (jj_3R_301()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_428() {
    if (jj_scan_token(TESTAAAA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_103() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_216()) {
    jj_scanpos = xsp;
    if (jj_3R_217()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_218()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_120() {
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(ASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_423() {
    if (jj_scan_token(ABSTRACT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_270() {
    if (jj_scan_token(AT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_90()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_122()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_477() {
    if (jj_3R_136()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_149() {
    if (jj_scan_token(STATIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_83() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_149()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_150()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_271() {
    if (jj_scan_token(AT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_90()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_427() {
    if (jj_scan_token(PRIVATE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_18() {
    if (jj_3R_96()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_426() {
    if (jj_scan_token(PROTECTED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_425() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_45() {
    if (jj_scan_token(AT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_90()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_422() {
    if (jj_scan_token(STATIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_397() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_422()) {
    jj_scanpos = xsp;
    if (jj_3R_423()) {
    jj_scanpos = xsp;
    if (jj_3R_424()) {
    jj_scanpos = xsp;
    if (jj_3R_425()) {
    jj_scanpos = xsp;
    if (jj_3R_426()) {
    jj_scanpos = xsp;
    if (jj_3R_427()) {
    jj_scanpos = xsp;
    if (jj_3R_428()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_269() {
    if (jj_scan_token(AT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_90()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_284()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_385() {
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_397()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_3R_304()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_44() {
    if (jj_scan_token(AT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_90()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_120()) {
    jj_scanpos = xsp;
    if (jj_3R_121()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_19() {
    if (jj_scan_token(THIS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_506() {
    if (jj_3R_96()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_234() {
    if (jj_3R_271()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_486() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_506()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SUPER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_264()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_233() {
    if (jj_3R_270()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_367() {
    if (jj_3R_372()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_430() {
    if (jj_scan_token(EXTENDS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_90()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_477()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_485() {
    if (jj_scan_token(THIS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_264()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_451() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_485()) {
    jj_scanpos = xsp;
    if (jj_3R_486()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_143() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_232()) {
    jj_scanpos = xsp;
    if (jj_3R_233()) {
    jj_scanpos = xsp;
    if (jj_3R_234()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_232() {
    if (jj_3R_269()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_358() {
    if (jj_scan_token(LBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_367()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(RBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_193() {
    if (jj_scan_token(ASSERT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_99() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_192()) {
    jj_scanpos = xsp;
    if (jj_3R_193()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_192() {
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_545() {
    if (jj_scan_token(FINALLY)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_150()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_544() {
    if (jj_scan_token(CATCH)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_481()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_150()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_429() {
    if (jj_3R_168()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_356() {
    if (jj_scan_token(TRY)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_150()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_544()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    xsp = jj_scanpos;
    if (jj_3R_545()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_355() {
    if (jj_scan_token(SYNCHRONIZED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_150()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_354() {
    if (jj_scan_token(THROW)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_543() {
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_304() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(CLASS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_429()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_430()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_431()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_358()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_407() {
    if (jj_3R_272()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_542() {
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_119() {
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_353() {
    if (jj_scan_token(RETURN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_543()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_579() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_345()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_561() {
    if (jj_3R_571()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_541() {
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_118() {
    if (jj_scan_token(ENUM)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_352() {
    if (jj_scan_token(CONTINUE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_542()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_351() {
    if (jj_scan_token(BREAK)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_541()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_95() {
    if (jj_3R_96()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_17() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_95()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SUPER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_16() {
    if (jj_scan_token(THIS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_571() {
    if (jj_3R_576()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_117() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_43() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_117()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_118()) {
    jj_scanpos = xsp;
    if (jj_3R_119()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_560() {
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_576() {
    if (jj_3R_345()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_579()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_406() {
    if (jj_3R_451()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_405() {
    if (jj_3R_451()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_538() {
    if (jj_scan_token(ELSE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_303()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_575() {
    if (jj_3R_576()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_42() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(COLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_574() {
    if (jj_3R_302()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_570() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_574()) {
    jj_scanpos = xsp;
    if (jj_3R_575()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_404() {
    if (jj_scan_token(THROWS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_450()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_559() {
    if (jj_3R_570()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_539() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(COLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_540() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_559()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_560()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_561()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_350() {
    if (jj_scan_token(FOR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_539()) {
    jj_scanpos = xsp;
    if (jj_3R_540()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_303()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_402() {
    if (jj_3R_168()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_148() {
    if (jj_scan_token(TESTAAAA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_349() {
    if (jj_scan_token(DO)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_303()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(WHILE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_448() {
    if (jj_scan_token(PRIVATE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_348() {
    if (jj_scan_token(WHILE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_303()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_447() {
    if (jj_scan_token(PROTECTED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_446() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_401() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_446()) {
    jj_scanpos = xsp;
    if (jj_3R_447()) {
    jj_scanpos = xsp;
    if (jj_3R_448()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_347() {
    if (jj_scan_token(IF)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_303()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_538()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_389() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_401()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_402()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_99()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_403()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_404()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_405()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_406()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_407()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(RBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_147() {
    if (jj_scan_token(SYNCHRONIZED)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_558() {
    if (jj_3R_272()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_504() {
    if (jj_scan_token(ELLIPSIS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_146() {
    if (jj_scan_token(PUBLIC)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_569() {
    if (jj_scan_token(_DEFAULT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(COLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_481() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_103()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_504()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_469()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_116() {
    if (jj_scan_token(LBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RBRACKET)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_568() {
    if (jj_scan_token(CASE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(COLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_557() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_568()) {
    jj_scanpos = xsp;
    if (jj_3R_569()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_145() {
    if (jj_scan_token(FINAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_115() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_537() {
    if (jj_3R_557()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_558()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_517() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_418()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_346() {
    if (jj_scan_token(SWITCH)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_537()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    if (jj_scan_token(RBRACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_449() {
    if (jj_3R_481()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_482()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_114() {
    if (jj_scan_token(ASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_482() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_481()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_403() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_449()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(RPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_113() {
    if (jj_scan_token(SEMICOLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_144() {
    if (jj_scan_token(ABSTRACT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_4() {
    if (jj_3R_82()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(INTERFACE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_81() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_144()) {
    jj_scanpos = xsp;
    if (jj_3R_145()) {
    jj_scanpos = xsp;
    if (jj_3R_146()) {
    jj_scanpos = xsp;
    if (jj_3R_147()) {
    jj_scanpos = xsp;
    if (jj_3R_148()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_567() {
    if (jj_3R_100()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_108()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_566() {
    if (jj_scan_token(DECR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  public JavaParserTokenManager token_source;
  ASCII_UCodeESC_CharStream jj_input_stream;
  public Token token, jj_nt;
  private int jj_ntk;
  private Token jj_scanpos, jj_lastpos;
  private int jj_la;
  public boolean lookingAhead = false;
  private boolean jj_semLA;
  private int jj_gen;
  final private int[] jj_la1 = new int[196];
  final private int[] jj_la1_0 = {0x0,0x90202000,0x0,0x0,0x0,0x90202000,0x0,0x0,0x80002000,0x80002000,0x200000,0x0,0x0,0x0,0x0,0x0,0x80002000,0x80002000,0x80002000,0x0,0x0,0x20000000,0x0,0x9432e000,0x80002000,0x80002000,0x0,0x0,0x0,0x0,0x9412c000,0x0,0x80002000,0x80002000,0x0,0x0,0x2000,0x2000,0x80002000,0x80002000,0x0,0x20000000,0x9432e000,0x0,0x9412c000,0x80000000,0x80000000,0x0,0x0,0x0,0x10004000,0x0,0x5412c000,0x5412c000,0x0,0x0,0x80002000,0x80002000,0x0,0x0,0x0,0x0,0x0,0x0,0x9412e000,0x0,0x0,0x0,0x0,0x0,0xd6b3e000,0x5412c000,0x0,0x0,0x0,0x1412c000,0x0,0x4128000,0x0,0x4128000,0x1412c000,0x10004000,0x10004000,0x0,0x0,0x0,0x5412c000,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x5412c000,0x0,0x0,0x5412c000,0x40004000,0x0,0x0,0x0,0x0,0x40000000,0x0,0x10004000,0x0,0x40000000,0x40000000,0x5412c000,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x5693c000,0x0,0xd6b3e000,0xd6b3e000,0x80000000,0x0,0x0,0x0,0x5412c000,0x1040000,0xd6b3e000,0x1040000,0x8000000,0xd412c000,0x5412c000,0x5412c000,0xd412c000,0x5412c000,0x0,0x4000,0x4000,0x5412c000,0x80000,0x0,0x4000,0x0,0x0,0x0,0x5412c000,0x5412c000,0x0,0x9432e000,0x1000000,0x1432c000,0x9432e000,0x80002000,0x200000,0x0,0x20000000,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x9432e000,0x80002000,0x0,0x9432e000,0x0,0x20000000,0x10200000,0x9412e000,0x9432e000,0x0,0x0,0x0,0x20000000,0x4128000,0x4128000,0x20000000,0x0,0x0,0x0,0x9412e000,0x0,};
  final private int[] jj_la1_1 = {0x40,0x11338a00,0x4040,0x4040,0x40,0x11338a00,0x4000,0x40,0x1220000,0x1220000,0x200,0x0,0x100000,0x0,0x0,0x0,0x11338800,0x1220000,0x1220000,0x0,0x0,0x0,0x20,0x913b8f02,0x338000,0x338000,0x0,0x0,0x0,0x0,0x101b8502,0x0,0x1338800,0x1338800,0x0,0x0,0x220000,0x220000,0x338000,0x338000,0x0,0x0,0x913b8f02,0x0,0x101b8502,0x10138000,0x10138000,0x0,0x0,0x0,0x0,0x0,0xa2483502,0xa2483502,0x0,0x0,0x1338800,0x1338800,0x0,0x0,0x8000000,0x0,0x0,0x0,0x113b8d02,0x0,0x38000,0x38000,0x0,0x8000000,0xf7ffbf16,0xa2483502,0x100000,0x0,0x0,0x80502,0x0,0x80502,0x0,0x80502,0x80080502,0x0,0x0,0x0,0x0,0x0,0xa2483502,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x80,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0xa2483502,0x0,0x0,0xa2483502,0x22403000,0x0,0x0,0x0,0x0,0x22002000,0x1000,0x0,0x0,0x20002000,0x20000000,0xa2483502,0x0,0x0,0x0,0x0,0x1000,0x0,0x0,0xe7cc3516,0x0,0xf7ffbf16,0xf7ffbf16,0x0,0x0,0x0,0x0,0xa2483502,0x0,0xf7ffbf16,0x0,0x0,0xa2483502,0xa2483502,0xa2483502,0xa2483502,0xa2483502,0x0,0x0,0x0,0xa2483502,0x0,0x1,0x0,0x0,0x0,0x0,0xa2483502,0xa2483502,0x0,0x113b8f02,0x0,0x80702,0x113b8f02,0x11338800,0x200,0x0,0x0,0x20,0x20,0x0,0x0,0x0,0x0,0x0,0x913b8f02,0x11338800,0x0,0x913b8f02,0x0,0x0,0x200,0x813b8d02,0x913b8f02,0x0,0x0,0x0,0x400000,0x80502,0x80502,0x400000,0x0,0x0,0x0,0x113b8d02,0x0,};
  final private int[] jj_la1_2 = {0x0,0x480001,0x400000,0x400000,0x0,0x480001,0x400000,0x0,0x0,0x0,0x0,0x400000,0x0,0x200000,0x400000,0x400000,0x480001,0x0,0x0,0x2000000,0x2000000,0x0,0x0,0x2488401,0x0,0x0,0x400000,0x400000,0x400000,0x80000,0x400401,0x400000,0x0,0x0,0x400000,0x2000000,0x0,0x0,0x0,0x0,0x2000000,0x0,0x2480401,0x80000,0x400401,0x1,0x1,0x400000,0x100000,0x800000,0x400,0x20000,0xc00a744,0xc00a744,0x100000,0x400000,0x0,0x0,0x400000,0x2000000,0x0,0x88000,0x20000,0x100000,0x400401,0x0,0x0,0x0,0x2000000,0x0,0x48a747,0x2744,0x0,0x200000,0x2000000,0x400,0x20000,0x400,0x20000,0x0,0x400,0x400,0x400,0x2000000,0x100000,0x2000000,0xc002744,0x800000,0x10000000,0x0,0x0,0x0,0x0,0x0,0x40000000,0x40000000,0x0,0x83000000,0x83000000,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0xc002744,0xc000000,0xc000000,0x2744,0xc002744,0x2000,0x0,0x0,0x2000,0x344,0x2000,0x400,0x222000,0x344,0x0,0xc002744,0x100000,0x2000000,0x8000,0x22000,0x0,0x20000,0x20000,0x8a746,0x20000000,0x48a747,0x48a747,0x0,0x100000,0x800000,0x800000,0x2744,0x0,0x48a747,0x0,0x0,0x2744,0xc002744,0x2744,0x82744,0x2744,0x100000,0x400,0x400,0xc002744,0x0,0x0,0x400,0x400000,0x400,0x100000,0xc40a744,0xc40a744,0x100000,0x480401,0x0,0x400400,0x480401,0x400001,0x0,0x2000000,0x0,0x0,0x0,0x100000,0x1000000,0x1000000,0x100000,0x100000,0x2488401,0x400401,0x100000,0x2488401,0x80000,0x0,0x0,0x2400400,0x2480401,0x2000,0x8000,0x0,0x0,0x10000400,0x400,0x0,0x100000,0x20000,0x100000,0x400401,0x0,};
  final private int[] jj_la1_3 = {0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0xf0,0xf0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x10000000,0x0,0x0,0x0,0x0,0x30,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0xf0,0xffe0000,0x0,0x4,0x8,0x800,0x1000,0x400,0x2,0x2,0x0,0x1,0x1,0x1c000,0x1c000,0xc0,0xc0,0x2300,0x2300,0xc0,0xf0,0x0,0x0,0x0,0x0,0x0,0x30,0x30,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0xf0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x30,0x0,0x30,0x30,0x0,0x0,0xffe0030,0xffe0030,0x30,0x0,0x30,0x0,0x0,0x30,0xf0,0x30,0x30,0x30,0x0,0x0,0x0,0xf0,0x0,0x0,0x0,0x0,0x0,0x0,0xf0,0xf0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x18000,0x18000,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x400,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x10000000,};
  final private JJCalls[] jj_2_rtns = new JJCalls[62];
  private boolean jj_rescan = false;
  private int jj_gc = 0;

  public JavaParser(java.io.InputStream stream) {
    jj_input_stream = new ASCII_UCodeESC_CharStream(stream, 1, 1);
    token_source = new JavaParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 196; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void ReInit(java.io.InputStream stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 196; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public JavaParser(java.io.Reader stream) {
    jj_input_stream = new ASCII_UCodeESC_CharStream(stream, 1, 1);
    token_source = new JavaParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 196; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void ReInit(java.io.Reader stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 196; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public JavaParser(JavaParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 196; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void ReInit(JavaParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 196; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  final private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken;
    if ((oldToken = token).next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    if (token.kind == kind) {
      jj_gen++;
      if (++jj_gc > 100) {
        jj_gc = 0;
        for (int i = 0; i < jj_2_rtns.length; i++) {
          JJCalls c = jj_2_rtns[i];
          while (c != null) {
            if (c.gen < jj_gen) c.first = null;
            c = c.next;
          }
        }
      }
      return token;
    }
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }

  final private boolean jj_scan_token(int kind) {
    if (jj_scanpos == jj_lastpos) {
      jj_la--;
      if (jj_scanpos.next == null) {
        jj_lastpos = jj_scanpos = jj_scanpos.next = token_source.getNextToken();
      } else {
        jj_lastpos = jj_scanpos = jj_scanpos.next;
      }
    } else {
      jj_scanpos = jj_scanpos.next;
    }
    if (jj_rescan) {
      int i = 0; Token tok = token;
      while (tok != null && tok != jj_scanpos) { i++; tok = tok.next; }
      if (tok != null) jj_add_error_token(kind, i);
    }
    return (jj_scanpos.kind != kind);
  }

  final public Token getNextToken() {
    if (token.next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    jj_gen++;
    return token;
  }

  final public Token getToken(int index) {
    Token t = lookingAhead ? jj_scanpos : token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  final private int jj_ntk() {
    if ((jj_nt=token.next) == null)
      return (jj_ntk = (token.next=token_source.getNextToken()).kind);
    else
      return (jj_ntk = jj_nt.kind);
  }

  private java.util.Vector jj_expentries = new java.util.Vector();
  private int[] jj_expentry;
  private int jj_kind = -1;
  private int[] jj_lasttokens = new int[100];
  private int jj_endpos;

  private void jj_add_error_token(int kind, int pos) {
    if (pos >= 100) return;
    if (pos == jj_endpos + 1) {
      jj_lasttokens[jj_endpos++] = kind;
    } else if (jj_endpos != 0) {
      jj_expentry = new int[jj_endpos];
      for (int i = 0; i < jj_endpos; i++) {
        jj_expentry[i] = jj_lasttokens[i];
      }
      boolean exists = false;
      for (java.util.Enumeration enu = jj_expentries.elements(); enu.hasMoreElements();) {
        int[] oldentry = (int[])(enu.nextElement());
        if (oldentry.length == jj_expentry.length) {
          exists = true;
          for (int i = 0; i < jj_expentry.length; i++) {
            if (oldentry[i] != jj_expentry[i]) {
              exists = false;
              break;
            }
          }
          if (exists) break;
        }
      }
      if (!exists) jj_expentries.addElement(jj_expentry);
      if (pos != 0) jj_lasttokens[(jj_endpos = pos) - 1] = kind;
    }
  }

  final public ParseException generateParseException() {
    jj_expentries.removeAllElements();
    boolean[] la1tokens = new boolean[125];
    for (int i = 0; i < 125; i++) {
      la1tokens[i] = false;
    }
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 196; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
          if ((jj_la1_1[i] & (1<<j)) != 0) {
            la1tokens[32+j] = true;
          }
          if ((jj_la1_2[i] & (1<<j)) != 0) {
            la1tokens[64+j] = true;
          }
          if ((jj_la1_3[i] & (1<<j)) != 0) {
            la1tokens[96+j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 125; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.addElement(jj_expentry);
      }
    }
    jj_endpos = 0;
    jj_rescan_token();
    jj_add_error_token(0, 0);
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = (int[])jj_expentries.elementAt(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }

  final public void enable_tracing() {
  }

  final public void disable_tracing() {
  }

  final private void jj_rescan_token() {
    jj_rescan = true;
    for (int i = 0; i < 62; i++) {
      JJCalls p = jj_2_rtns[i];
      do {
        if (p.gen > jj_gen) {
          jj_la = p.arg; jj_lastpos = jj_scanpos = p.first;
          switch (i) {
            case 0: jj_3_1(); break;
            case 1: jj_3_2(); break;
            case 2: jj_3_3(); break;
            case 3: jj_3_4(); break;
            case 4: jj_3_5(); break;
            case 5: jj_3_6(); break;
            case 6: jj_3_7(); break;
            case 7: jj_3_8(); break;
            case 8: jj_3_9(); break;
            case 9: jj_3_10(); break;
            case 10: jj_3_11(); break;
            case 11: jj_3_12(); break;
            case 12: jj_3_13(); break;
            case 13: jj_3_14(); break;
            case 14: jj_3_15(); break;
            case 15: jj_3_16(); break;
            case 16: jj_3_17(); break;
            case 17: jj_3_18(); break;
            case 18: jj_3_19(); break;
            case 19: jj_3_20(); break;
            case 20: jj_3_21(); break;
            case 21: jj_3_22(); break;
            case 22: jj_3_23(); break;
            case 23: jj_3_24(); break;
            case 24: jj_3_25(); break;
            case 25: jj_3_26(); break;
            case 26: jj_3_27(); break;
            case 27: jj_3_28(); break;
            case 28: jj_3_29(); break;
            case 29: jj_3_30(); break;
            case 30: jj_3_31(); break;
            case 31: jj_3_32(); break;
            case 32: jj_3_33(); break;
            case 33: jj_3_34(); break;
            case 34: jj_3_35(); break;
            case 35: jj_3_36(); break;
            case 36: jj_3_37(); break;
            case 37: jj_3_38(); break;
            case 38: jj_3_39(); break;
            case 39: jj_3_40(); break;
            case 40: jj_3_41(); break;
            case 41: jj_3_42(); break;
            case 42: jj_3_43(); break;
            case 43: jj_3_44(); break;
            case 44: jj_3_45(); break;
            case 45: jj_3_46(); break;
            case 46: jj_3_47(); break;
            case 47: jj_3_48(); break;
            case 48: jj_3_49(); break;
            case 49: jj_3_50(); break;
            case 50: jj_3_51(); break;
            case 51: jj_3_52(); break;
            case 52: jj_3_53(); break;
            case 53: jj_3_54(); break;
            case 54: jj_3_55(); break;
            case 55: jj_3_56(); break;
            case 56: jj_3_57(); break;
            case 57: jj_3_58(); break;
            case 58: jj_3_59(); break;
            case 59: jj_3_60(); break;
            case 60: jj_3_61(); break;
            case 61: jj_3_62(); break;
          }
        }
        p = p.next;
      } while (p != null);
    }
    jj_rescan = false;
  }

  final private void jj_save(int index, int xla) {
    JJCalls p = jj_2_rtns[index];
    while (p.gen > jj_gen) {
      if (p.next == null) { p = p.next = new JJCalls(); break; }
      p = p.next;
    }
    p.gen = jj_gen + xla - jj_la; p.first = token; p.arg = xla;
  }

  static final class JJCalls {
    int gen;
    Token first;
    int arg;
    JJCalls next;
  }

}
